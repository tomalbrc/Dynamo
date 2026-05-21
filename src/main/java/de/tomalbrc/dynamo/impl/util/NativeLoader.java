package de.tomalbrc.dynamo.impl.util;

import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;

public class NativeLoader {
    public static void load() {
        try {
            LibraryInfo info = new LibraryInfo(null, "joltjni", DirectoryPath.USER_DIR);
            NativeBinaryLoader loader = new NativeBinaryLoader(info);

            // TODO: are those correct with the Linux64_fma and Windows64_avx2 versions?
            NativeDynamicLibrary[] libraries = {
                    new NativeDynamicLibrary("linux/aarch64/com/github/stephengold", PlatformPredicate.LINUX_ARM_64),
                    new NativeDynamicLibrary("linux/armhf/com/github/stephengold", PlatformPredicate.LINUX_ARM_32),
                    new NativeDynamicLibrary("linux/x86-64/com/github/stephengold", PlatformPredicate.LINUX_X86_64),
                    new NativeDynamicLibrary("osx/aarch64/com/github/stephengold", PlatformPredicate.MACOS_ARM_64),
                    new NativeDynamicLibrary("osx/x86-64/com/github/stephengold", PlatformPredicate.MACOS_X86_64),
                    new NativeDynamicLibrary("windows/x86-64/com/github/stephengold", PlatformPredicate.WIN_X86_64)
            };
            loader.registerNativeLibraries(libraries).initPlatformLibrary();
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
