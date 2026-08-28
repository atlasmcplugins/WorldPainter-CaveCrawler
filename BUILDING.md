# Building WorldPainter
## Installing dependencies
All of WorldPainter's dependencies resolve from Maven Central or from WorldPainter's own repository. Nothing needs to
be installed into your local Maven repository by hand.

> **Note:** upstream WorldPainter requires the commercial [JIDE Docking Framework](https://www.jidesoft.com/products/dock.htm)
> to be downloaded and installed manually. This fork does not: the docks are provided by
> [Modern Docking](https://github.com/andrewauclair/ModernDocking) (Apache 2.0), behind the
> `org.pepsoft.worldpainter.util.docking` abstraction, and the handful of plain Swing controls WorldPainter borrows from
> JIDE come from the open source `jide-oss` artifact on Maven Central.

## Set up Maven toolchain
WorldPainter uses the [Maven toolchain framework](https://maven.apache.org/guides/mini/guide-using-toolchains.html) to find the JDK it needs. You need to follow the instructions on that page to configure a toolchain of type jdk and version 17 pointing to a Java 17 JDK. Note that it has not been tested whether WorldPainter will run correctly on older Java versions if you substitute a newer JDK for version 17, although in theory that should work.

A JDK 21 has been used successfully for this fork, by declaring it as satisfying version 17 in `~/.m2/toolchains.xml`;
the compiler is still pinned to `-source 17 -target 17`. Note that this requires Lombok 1.18.30 or later, which is why
this fork bumps it: Lombok 1.18.22, which upstream pins, crashes on JDK 21 with
`NoSuchFieldError: ... JCTree$JCImport does not have member field ... qualid`.

## Build WorldPainter
Once all dependencies are installed and the toolchains set up you can build WorldPainter from the command line or using your favourite IDE by invoking the `install` goal on the `WorldPainter` module. There are some rudimentary tests, but they take a while to run and don't contribute much, so I recommend skipping them by adding `-DskipTests=true`.

## Run WorldPainter
Once it is built, you can run WorldPainter by invoking the `exec:exec` goal on the `WPGUI` module, or by running the main class: `org.pepsoft.worldpainter.Main`.

## Develop WorldPainter
For a few pointers, pitfalls and gotchas about developing WorldPainter, see [this page](https://www.worldpainter.net/trac/wiki/DevelopingWorldPainter).

## More details
For a more detailed description of the build process, see: https://www.worldpainter.net/doc/building.
