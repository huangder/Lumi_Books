#ifndef LUMI_MOBI_BUDGET_H
#define LUMI_MOBI_BUDGET_H
#include <stddef.h>
#ifdef _WIN32
#define LUMI_EXPORT __declspec(dllexport)
#else
#define LUMI_EXPORT __attribute__((visibility("default")))
#endif
#ifdef __cplusplus
extern "C" {
#endif
void *lumi_mobi_malloc(size_t size);
void *lumi_mobi_calloc(size_t count, size_t size);
void *lumi_mobi_realloc(void *ptr, size_t size);
void lumi_mobi_free(void *ptr);
char *lumi_mobi_strdup(const char *text);
LUMI_EXPORT void lumi_mobi_budget_begin(void);
LUMI_EXPORT int lumi_mobi_budget_exceeded(void);
#ifdef __cplusplus
}
#endif
#ifdef LUMI_MOBI_WRAP_ALLOC
#define malloc lumi_mobi_malloc
#define calloc lumi_mobi_calloc
#define realloc lumi_mobi_realloc
#define free lumi_mobi_free
#define strdup lumi_mobi_strdup
#endif
#endif
