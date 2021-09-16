/* Copyright (c) 2013-2021 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba/core/config.h>
#include <mgba/core/version.h>
#include <mgba/feature/updater.h>
#include <mgba-util/string.h>
#include <mgba-util/vfs.h>

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>

#ifdef _WIN32
#include <direct.h>
#include <io.h>
#include <process.h>
#include <synchapi.h>

#define mkdir(X, Y) _mkdir(X)
#elif defined(_POSIX_C_SOURCE)
#include <unistd.h>
#endif

#ifndef W_OK
#define W_OK 02
#endif

bool extractArchive(struct VDir* archive, const char* root, bool prefix) {
	char path[PATH_MAX] = {0};
	struct VDirEntry* vde;
	uint8_t block[8192];
	ssize_t size;
	while ((vde = archive->listNext(archive))) {
		struct VFile* vfIn;
		struct VFile* vfOut;
		const char* fname;
		if (prefix) {
			fname = strchr(vde->name(vde), '/');
			if (!fname) {
				continue;
			}
			snprintf(path, sizeof(path), "%s/%s", root, &fname[1]);
		} else {
			fname = vde->name(vde);
			snprintf(path, sizeof(path), "%s/%s", root, fname);
		}
		if (fname[0] == '.') {
			continue;
		}
		switch (vde->type(vde)) {
		case VFS_DIRECTORY:
			printf("mkdir   %s\n", fname);
			if (mkdir(path, 0755) < 0 && errno != EEXIST) {
				return false;
			}
			if (!prefix) {
				struct VDir* subdir = archive->openDir(archive, fname);
				if (!subdir) {
					return false;
				}
				if (!extractArchive(subdir, path, false)) {
					subdir->close(subdir);
					return false;
				}
				subdir->close(subdir);
			}
			break;
		case VFS_FILE:
			printf("extract %s\n", fname);
			vfIn = archive->openFile(archive, vde->name(vde), O_RDONLY);
			errno = 0;
			vfOut = VFileOpen(path, O_WRONLY | O_CREAT | O_TRUNC);
			if (!vfOut && errno == EACCES) {
#ifdef _WIN32
				Sleep(1000);
#else
				sleep(1);
#endif
				vfOut = VFileOpen(path, O_WRONLY | O_CREAT | O_TRUNC);
			}
			if (!vfOut) {
				vfIn->close(vfIn);
				return false;
			}
			while ((size = vfIn->read(vfIn, block, sizeof(block))) > 0) {
				vfOut->write(vfOut, block, size);
			}
			vfOut->close(vfOut);
			vfIn->close(vfIn);
			if (size < 0) {
				return false;
			}
			break;
		case VFS_UNKNOWN:
			return false;
		}
	}
	return true;
}

int main(int argc, char* argv[]) {
	UNUSED(argc);
	UNUSED(argv);
	struct mCoreConfig config;
	char updateArchive[PATH_MAX] = {0};
	char bin[PATH_MAX] = {0};
	const char* root;
	int ok = 1;

	mCoreConfigInit(&config, "updater");
	if (!mCoreConfigLoad(&config)) {
		puts("Failed to load config");
	} else if (!mUpdateGetArchivePath(&config, updateArchive, sizeof(updateArchive)) || !(root = mUpdateGetRoot(&config))) {
		puts("No pending update found");
	} else if (access(root, W_OK)) {
		puts("Cannot write to update path");
	} else {
#ifdef __APPLE__
		char subdir[PATH_MAX];
		char devpath[PATH_MAX] = {0};
		bool needsUnmount = false;
#endif
		bool isPortable = mCoreConfigIsPortable();
		const char* extension = mUpdateGetArchiveExtension(&config);
		struct VDir* archive = NULL;
		bool prefix = true;
		if (strcmp(extension, "dmg") == 0) {
#ifdef __APPLE__
			char mountpoint[PATH_MAX];
			// Make a slightly random directory name for the updater mountpoint
			struct timeval t;
			gettimeofday(&t, NULL);
			int printed = snprintf(mountpoint, sizeof(mountpoint), "/Volumes/%s Updater %04X", projectName, (t.tv_usec >> 2) & 0xFFFF);

			// Fork hdiutil to mount it
			char* args[] = {"hdiutil", "attach", "-nobrowse", "-mountpoint", mountpoint, updateArchive, NULL};
			int fds[2];
			pipe(fds);
			pid_t pid = fork();
			if (pid == 0) {
				dup2(fds[1], STDOUT_FILENO);
				execvp("hdiutil", args);
				_exit(1);
			} else {
				// Parse out the disk ID so we can detach it when we're done
				char buffer[1024] = {0};
				ssize_t size;
				while ((size = read(fds[0], buffer, sizeof(buffer) - 1)) > 0) { // Leave the last byte null
					char* devinfo = strnstr(buffer, "\n/dev/disk", size);
					if (!devinfo) {
						continue;
					}
					char* devend = strpbrk(&devinfo[9], "s \t");
					if (!devend) {
						continue;
					}
					off_t diff = devend - devinfo - 1;
					memcpy(devpath, &devinfo[1], diff);
					puts(devpath);
					break;
				}
				int retstat;
				wait4(pid, &retstat, 0, NULL);
			}
			snprintf(&mountpoint[printed], sizeof(mountpoint) - printed, "/%s.app", projectName);
			snprintf(subdir, sizeof(subdir), "%s/%s.app", root, projectName);
			root = subdir;
			archive = VDirOpen(mountpoint);
			prefix = false;
			needsUnmount = true;
#endif
		} else {
			archive = VDirOpenArchive(updateArchive);
		}
		if (!archive) {
			puts("Cannot open update archive");
		} else {
			puts("Extracting update");
			if (extractArchive(archive, root, prefix)) {
				puts("Complete");
				const char* command = mUpdateGetCommand(&config);
				strlcpy(bin, command, sizeof(bin));
				ok = 0;
				mUpdateDeregister(&config);
			} else {
				puts("An error occurred");
			}
			archive->close(archive);
			unlink(updateArchive);
		}
#ifdef __APPLE__
		if (needsUnmount) {
			char* args[] = {"hdiutil", "detach", devpath, NULL};
			pid_t pid = vfork();
			if (pid == 0) {
				execvp("hdiutil", args);
				_exit(0);
			} else {
				int retstat;
				wait4(pid, &retstat, 0, NULL);
			}
		}
#endif
		if (!isPortable) {
			char portableIni[PATH_MAX] = {0};
			snprintf(portableIni, sizeof(portableIni), "%s/portable.ini", root);
			unlink(portableIni);
		}
	}
	mCoreConfigDeinit(&config);
	if (ok == 0) {
		const char* argv[] = { bin, NULL };
#ifdef _WIN32
		_execv(bin, argv);
#elif defined(_POSIX_C_SOURCE) || defined(__APPLE__)
		execv(bin, argv);
#endif
	}
	return 1;
}

#ifdef _WIN32
#include <mgba-util/string.h>
#include <mgba-util/vector.h>

int wmain(int argc, wchar_t* argv[]) {
	struct StringList argv8;
	StringListInit(&argv8, argc);
	for (int i = 0; i < argc; ++i) {
		*StringListAppend(&argv8) = utf16to8((uint16_t*) argv[i], wcslen(argv[i]) * 2);
	}
	int ret = main(argc, StringListGetPointer(&argv8, 0));

	size_t i;
	for (i = 0; i < StringListSize(&argv8); ++i) {
		free(*StringListGetPointer(&argv8, i));
	}
	return ret;
}
#endif
