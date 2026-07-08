//
// Created by maks on 24.09.2022.
//

#include <stdlib.h>
#include <android/log.h>
#include <assert.h>
#include <string.h>
#include "environ.h"
#define TAG __FILE_NAME__
#include <log.h>

struct pojav_environ_s *pojav_environ;
__attribute__((constructor)) void env_init() {
    // Check both POJAV_ENVIRON (used by prebuilt LWJGL/MC libraries) and ONYX_ENVIRON (our name).
    // POJAV_ENVIRON must take priority because LWJGL and other prebuilt Minecraft libraries
    // look for this exact variable name to share the pojav_environ pointer across libraries.
    char* strptr_env = getenv("POJAV_ENVIRON");
    if(strptr_env == NULL) {
        strptr_env = getenv("ONYX_ENVIRON");
    }

    if(strptr_env == NULL) {
        LOGI("No environ found, creating...");
        pojav_environ = malloc(sizeof(struct pojav_environ_s));
        assert(pojav_environ);
        memset(pojav_environ, 0 , sizeof(struct pojav_environ_s));
        
        const char *renderer = getenv("ONYX_RENDERER");
        if (renderer) {
            if (strncmp("opengles", renderer, 8) == 0) {
                pojav_environ->config_renderer = RENDERER_GL4ES;
            } else if (strcmp(renderer, "vulkan_zink") == 0) {
                pojav_environ->config_renderer = RENDERER_VK_ZINK;
            } else {
                pojav_environ->config_renderer = RENDERER_GL4ES;
            }
        } else {
            pojav_environ->config_renderer = RENDERER_GL4ES;
        }

        if(asprintf(&strptr_env, "%p", pojav_environ) == -1) abort();
        // Set BOTH names so prebuilt LWJGL libs (POJAV_ENVIRON) and our libs (ONYX_ENVIRON)
        // can all find the shared environ pointer.
        setenv("POJAV_ENVIRON", strptr_env, 1);
        setenv("ONYX_ENVIRON", strptr_env, 1);
        free(strptr_env);
    }else{
        LOGI("Found existing environ: %s", strptr_env);
        pojav_environ = (void*) strtoul(strptr_env, NULL, 0x10);
        // Make sure both env vars point to the same environ
        char* onyx_env = getenv("ONYX_ENVIRON");
        char* pojav_env = getenv("POJAV_ENVIRON");
        if(onyx_env == NULL) setenv("ONYX_ENVIRON", strptr_env, 1);
        if(pojav_env == NULL) setenv("POJAV_ENVIRON", strptr_env, 1);
    }
    LOGI("%p", pojav_environ);
}