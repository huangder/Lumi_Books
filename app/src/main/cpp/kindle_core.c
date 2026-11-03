#include "kindle_core.h"
#include "mobi.h"
#include "mobi_budget.h"
#include <ctype.h>
#include <dirent.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#define LUMI_MAX_INPUT ((size_t)256 * 1024 * 1024)
#define LUMI_MAX_TEXT ((size_t)64 * 1024 * 1024)
#define LUMI_MAX_OUTPUT ((size_t)512 * 1024 * 1024)
#define LUMI_MAX_PART ((size_t)64 * 1024 * 1024)
#define LUMI_MAX_FILES 20000
static uint16_t be16(const unsigned char *p) { return (uint16_t)((p[0] << 8) | p[1]); }
static uint32_t be32(const unsigned char *p) { return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3]; }
/* Validate the complete PDB directory before libmobi allocates records. */
static int preflight(FILE *f) {
    unsigned char h[78], entry[8], record[16];
    if (fseek(f, 0, SEEK_END)) return 1;
    long end = ftell(f);
    if (end < 78) return 2;
    if ((unsigned long)end > LUMI_MAX_INPUT) return 5;
    rewind(f);
    if (fread(h, 1, sizeof h, f) != sizeof h) return 1;
    int mobi = memcmp(h + 60, "BOOKMOBI", 8) == 0;
    if (!mobi && memcmp(h + 60, "TEXtREAd", 8)) return 4;
    unsigned count = be16(h + 76);
    if (!count || 78u + (size_t)count * 8 > (size_t)end) return 2;
    uint32_t previous = 0, first = 0;
    size_t previousRecordEnd = 78u + (size_t)count * 8;
    for (unsigned i = 0; i < count; ++i) {
        if (fread(entry, 1, 8, f) != 8) return 1;
        uint32_t offset = be32(entry);
        if (offset < 78u + count * 8u || offset >= (uint32_t)end || (i && offset <= previous)) return 2;
        if (offset < previousRecordEnd) return 2;
        if (i + 1 < count) {
            long here = ftell(f);
            if (here < 0 || fseek(f, 78L + (long)(i + 1) * 8, SEEK_SET) || fread(entry, 1, 4, f) != 4) return 1;
            previousRecordEnd = be32(entry);
            if (previousRecordEnd <= offset || fseek(f, here, SEEK_SET)) return 2;
        } else previousRecordEnd = (uint32_t)end;
        if (!i) first = offset;
        if (i == 1 && offset - first < 16) return 2;
        previous = offset;
    }
    if ((size_t)first + 16 > (size_t)end || fseek(f, first, SEEK_SET) || fread(record, 1, 16, f) != 16) return 2;
    if (be16(record + 12)) return 3;
    if (!mobi) return 4;
    if (be32(record + 4) > LUMI_MAX_TEXT || (size_t)be16(record + 8) * 4096 > LUMI_MAX_TEXT) return 5;
    rewind(f);
    return 0;
}
static int empty_directory(const char *path) {
    struct stat info;
#ifdef _WIN32
    if (stat(path, &info)) return 0;
#else
    if (lstat(path, &info) || S_ISLNK(info.st_mode)) return 0;
#endif
    if (!S_ISDIR(info.st_mode)) return 0;
    DIR *d = opendir(path);
    if (!d) return 0;
    struct dirent *e;
    int empty = 1;
    while ((e = readdir(d))) {
        if (strcmp(e->d_name, ".") && strcmp(e->d_name, "..")) { empty = 0; break; }
    }
    closedir(d);
    return empty;
}
static void remove_outputs(const char *directory) {
    DIR *dir = opendir(directory);
    if (!dir) return;
    struct dirent *entry;
    char path[4096];
    while ((entry = readdir(dir))) {
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        int n = snprintf(path, sizeof path, "%s/%s", directory, entry->d_name);
        if (n > 0 && (size_t)n < sizeof path) unlink(path);
    }
    closedir(dir);
}
static int save_parts(const MOBIPart *part, const char *prefix, const char *directory,
                      size_t *total, size_t *files, int *opf) {
    for (; part; part = part->next) {
        if (++*files > LUMI_MAX_FILES || part->size > LUMI_MAX_PART || part->size > LUMI_MAX_OUTPUT - *total) return 5;
        if (part->size && !part->data) return 2;
        *total += part->size;
        if (!part->size) continue;
        MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
        const char *ext = meta.extension;
        if (!ext || !*ext || strlen(ext) > 12) return 2;
        for (const char *p = ext; *p; ++p) if (!isalnum((unsigned char)*p)) return 2;
        char name[80], path[4096];
        if (part->type == T_OPF) {
            if (++*opf != 1) return 2;
            strcpy(name, "content.opf");
        } else {
            snprintf(name, sizeof name, "%s%05zu.%s", prefix, part->uid, ext);
        }
        int n = snprintf(path, sizeof path, "%s/%s", directory, name);
        if (n < 0 || (size_t)n >= sizeof path) return 1;
        int flags = O_WRONLY | O_CREAT | O_EXCL;
#ifdef O_NOFOLLOW
        flags |= O_NOFOLLOW;
#endif
#ifdef O_BINARY
        flags |= O_BINARY;
#endif
        int fd = open(path, flags, 0600);
        if (fd < 0) return 1;
        FILE *f = fdopen(fd, "wb");
        if (!f) { close(fd); return 1; }
        int good = fwrite(part->data, 1, part->size, f) == part->size;
        if (fclose(f)) good = 0;
        if (!good) return 1;
    }
    return 0;
}
int lumi_kindle_reconstruct(const char *source, const char *output) {
    if (!source || !output || !empty_directory(output)) return 1;
    FILE *f = fopen(source, "rb");
    if (!f) return 1;
    int result = preflight(f);
    if (result) { fclose(f); return result; }
    lumi_mobi_budget_begin();
    MOBIData *m = mobi_init();
    MOBIRawml *raw = NULL;
    if (!m) { fclose(f); return 5; }
    m->use_kf8 = true;
    MOBI_RET status = mobi_load_file(m, f);
    fclose(f);
    if (status != MOBI_SUCCESS) { result = 2; goto done; }
    if (!m->rh) { result = 2; goto done; }
    if (m->rh->encryption_type || (m->next && m->next->rh && m->next->rh->encryption_type)) { result = 3; goto done; }
    if (mobi_is_replica(m) || mobi_is_dictionary(m) ||
        (mobi_is_kf8(m) && (!m->mh || !m->mh->version || *m->mh->version < 8))) { result = 4; goto done; }
    if (m->rh->text_length > LUMI_MAX_TEXT || mobi_get_text_maxsize(m) > LUMI_MAX_TEXT) { result = 5; goto done; }
    if (m->rh->compression_type != MOBI_COMPRESSION_NONE && m->rh->compression_type != MOBI_COMPRESSION_PALMDOC && m->rh->compression_type != MOBI_COMPRESSION_HUFFCDIC) { result = 4; goto done; }
    raw = mobi_init_rawml(m);
    if (!raw) { result = 5; goto done; }
    status = mobi_parse_rawml_opt(raw, m, true, false, true);
    if (status != MOBI_SUCCESS || !raw->markup) { result = 6; goto done; }
    size_t total = 0, files = 0;
    int opf = 0;
    result = save_parts(raw->markup, "part", output, &total, &files, &opf);
    if (!result && raw->flow) result = save_parts(raw->flow->next, "flow", output, &total, &files, &opf);
    if (!result) result = save_parts(raw->resources, "resource", output, &total, &files, &opf);
    if (!result && opf != 1) result = 6;
done:
    mobi_free_rawml(raw);
    mobi_free(m);
    if (lumi_mobi_budget_exceeded()) result = 5;
    if (result) remove_outputs(output);
    return result;
}
