#include "kindle_core.h"
#include <stdio.h>
int main(int argc, char **argv) {
    if (argc != 3) return 64;
    int result = lumi_kindle_reconstruct(argv[1], argv[2]);
    printf("reconstruct=%d\n", result);
    return result;
}
