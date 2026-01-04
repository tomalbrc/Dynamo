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
            LibraryInfo info = new LibraryInfo(null, "bulletjme", DirectoryPath.USER_DIR);
            NativeBinaryLoader loader = new NativeBinaryLoader(info);

            NativeDynamicLibrary[] libraries = {
                    new NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
                    new NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
                    new NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
                    new NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
                    new NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
                    new NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
            };
            loader.registerNativeLibraries(libraries).initPlatformLibrary();
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
