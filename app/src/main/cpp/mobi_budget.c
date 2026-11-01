#include "mobi_budget.h"
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdalign.h>
#define BUDGET ((size_t)512 * 1024 * 1024)
#define SINGLE ((size_t)128 * 1024 * 1024)
typedef union { size_t size; max_align_t alignment; } Header;
static _Thread_local size_t used, operations;
static _Thread_local int exceeded;
void lumi_mobi_budget_begin(void) { operations = 0; exceeded = 0; }
int lumi_mobi_budget_exceeded(void) { return exceeded; }
void *lumi_mobi_malloc(size_t size) {
    if (++operations > 2000000 || size > SINGLE || size > BUDGET - used) {
        exceeded = 1; return NULL;
    }
    Header *h = malloc(sizeof(Header) + size);
    if (!h) return NULL;
    h->size = size; used += size; return h + 1;
}
void lumi_mobi_free(void *ptr) {
    if (!ptr) return;
    Header *h = (Header *)ptr - 1;
    used -= h->size; free(h);
}
void *lumi_mobi_calloc(size_t count, size_t size) {
    if (size && count > SIZE_MAX / size) { exceeded = 1; return NULL; }
    size_t n = count * size;
    void *p = lumi_mobi_malloc(n);
    if (p) memset(p, 0, n);
    return p;
}
void *lumi_mobi_realloc(void *ptr, size_t size) {
    if (!ptr) return lumi_mobi_malloc(size);
    if (!size) { lumi_mobi_free(ptr); return NULL; }
    Header *old = (Header *)ptr - 1;
    void *p = lumi_mobi_malloc(size);
    if (p) { memcpy(p, ptr, old->size < size ? old->size : size); lumi_mobi_free(ptr); }
    return p;
}
char *lumi_mobi_strdup(const char *text) {
    size_t n = strlen(text) + 1;
    char *p = lumi_mobi_malloc(n);
    if (p) memcpy(p, text, n);
    return p;
}
