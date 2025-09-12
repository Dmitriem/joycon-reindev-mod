// simple joycond: scans /dev/input/event* looking for "Nintendo Switch" in device name, reads events
// Prints lines to stdout: "L ABS_X -105" "R BTN_SOUTH 1"
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dirent.h>
#include <fcntl.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <pthread.h>

struct devinfo { char path[256]; char name[256]; int fd; char side; };

int open_input(const char *path) {
    int fd = open(path, O_RDONLY);
    return fd;
}

void *reader_thread(void *arg) {
    struct devinfo *d = arg;
    struct input_event ev;
    while (1) {
        ssize_t n = read(d->fd, &ev, sizeof(ev));
        if (n == sizeof(ev)) {
            if (ev.type == EV_ABS) {
                const char *code = "";
                if (ev.code == ABS_X) code = "ABS_X";
                else if (ev.code == ABS_Y) code = "ABS_Y";
                else if (ev.code == ABS_RX) code = "ABS_RX";
                else if (ev.code == ABS_RY) code = "ABS_RY";
                else continue;
                printf("%c %s %d\n", d->side, code, ev.value);
                fflush(stdout);
            } else if (ev.type == EV_KEY) {
                // map key code names we care about (approx)
                const char *name = NULL;
                switch (ev.code) {
                    case BTN_SOUTH: name = "BTN_SOUTH"; break;
                    case BTN_EAST:  name = "BTN_EAST"; break;
                    case BTN_NORTH: name = "BTN_NORTH"; break;
                    case BTN_WEST:  name = "BTN_WEST"; break;
                    case BTN_Z:     name = "BTN_Z"; break;
                    case BTN_TL:    name = "BTN_TL"; break;
                    case BTN_TL2:   name = "BTN_TL2"; break;
                    case BTN_TR:    name = "BTN_TR"; break;
                    case BTN_TR2:   name = "BTN_TR2"; break;
                    case BTN_START: name = "BTN_START"; break;
                    case BTN_MODE:  name = "BTN_MODE"; break;
                    case BTN_THUMBL: name = "BTN_THUMBL"; break;
                    case BTN_THUMBR: name = "BTN_THUMBR"; break;
                    default: name = NULL; break;
                }
                if (name) {
                    printf("%c %s %d\n", d->side, name, ev.value);
                    fflush(stdout);
                }
            }
        } else {
            if (n < 0) {
                // read error
                // try sleep and continue
                usleep(10000);
            } else {
                usleep(10000);
            }
        }
    }
    return NULL;
}

int main(int argc, char **argv) {
    DIR *d = opendir("/dev/input");
    if (!d) {
        fprintf(stderr, "cannot open /dev/input\n");
        return 1;
    }
    struct dirent *de;
    struct devinfo infos[8];
    int ndev = 0;
    while ((de = readdir(d)) != NULL) {
        if (strncmp(de->d_name, "event", 5) != 0) continue;
        char path[256];
        snprintf(path, sizeof(path), "/dev/input/%s", de->d_name);
        int fd = open_input(path);
        if (fd < 0) continue;
        char name[256] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
            close(fd);
            continue;
        }
        // match either "Nintendo Switch Left Joy-Con" or Right
        if (strstr(name, "Nintendo Switch") || strstr(name, "Joy-Con")) {
            struct devinfo *di = &infos[ndev++];
            strncpy(di->path, path, sizeof(di->path)-1);
            strncpy(di->name, name, sizeof(di->name)-1);
            di->fd = fd;
            // decide side by name words
            if (strstr(name, "Left") || strstr(name, "left") || strstr(name, "joycon_00")) di->side = 'L';
            else di->side = 'R';
            // try grab to avoid double input (optional)
            ioctl(fd, EVIOCGRAB, (void*)1);
        } else {
            close(fd);
        }
    }
    closedir(d);

    for (int i=0;i<ndev;i++) {
        pthread_t th;
        pthread_create(&th, NULL, reader_thread, &infos[i]);
        pthread_detach(th);
    }

    // just wait forever while threads print
    while (1) sleep(60);
    return 0;
}
