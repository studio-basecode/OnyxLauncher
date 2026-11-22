//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>

#include <errno.h>

#define FREQ_MAX 256
#define MAX_CORES 64
void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

void bigcore_set_affinity() {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long freqs[MAX_CORES] = {0};
    unsigned long core_freq;
    unsigned long max_freq = 0;
    unsigned long min_freq = (unsigned long)-1;
    unsigned int corecnt = 0;
    unsigned int big_core_id = 0;
    while(corecnt < MAX_CORES) {
        bigcore_format_cpu_path(path_buffer, corecnt);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if(corefreqfd != -1) {
            ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
            close(corefreqfd);
            if(read_count <= 0) {
                corecnt++;
                continue;
            }
            freq_buffer[read_count] = 0;
            core_freq = strtoul(freq_buffer, &discard, 10);
            freqs[corecnt] = core_freq;
            if(core_freq >= max_freq) {
                max_freq = core_freq;
                big_core_id = corecnt;
            }
            if(core_freq > 0 && core_freq < min_freq) {
                min_freq = core_freq;
            }
        }else{
            break;
        }
        corecnt++;
    }
    if(corecnt == 0) {
        printf("bigcore: could not detect CPU core frequencies\n");
        return;
    }
    cpu_set_t bigcore_affinity_set;
    CPU_ZERO(&bigcore_affinity_set);
    unsigned int selected_count = 0;
    if(min_freq != (unsigned long)-1 && max_freq > min_freq) {
        for(unsigned int i = 0; i < corecnt; i++) {
            if(freqs[i] > min_freq) {
                CPU_SET_S(i, CPU_SETSIZE, &bigcore_affinity_set);
                selected_count++;
            }
        }
    }
    if(selected_count == 0) {
        CPU_SET_S(big_core_id, CPU_SETSIZE, &bigcore_affinity_set);
    }
    printf("bigcore: detected %u cores (min=%lu, max=%lu Hz, big_core=%u, mask_count=%u)\n",
           corecnt, min_freq == (unsigned long)-1 ? 0 : min_freq, max_freq, big_core_id, selected_count);
    int result = sched_setaffinity(0, CPU_SETSIZE, &bigcore_affinity_set);
    if(result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(errno));
    }else{
        printf("bigcore: forced current thread onto big/prime cores\n");
    }
}