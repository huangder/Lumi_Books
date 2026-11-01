#ifndef LUMI_KINDLE_H
#define LUMI_KINDLE_H
#ifdef __cplusplus
extern "C" {
#endif
/* Caller creates a new private empty directory, removes it on every failure. */
int lumi_kindle_reconstruct(const char *source, const char *output);
#ifdef __cplusplus
}
#endif
#endif
