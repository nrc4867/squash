# Squash

A tool for making cel-shaded, outlined cartoon characters: draw a part once, get a 3D body, pose it many times. Built on Stretch, the shared app shell.

- Plan: [Squash: plan](https://claude.ai/artifact/9sKH64ERAeZktGEtNmQBfP)
- Shell: [Stretch: design](https://claude.ai/artifact/VtgHzTtWDUYiVa6eKSqpJR)

## Build, run and test

Squash builds Stretch from source as a composite build. Point it at your Stretch checkout in `~/.gradle/gradle.properties`:

```
stretch.dir=C:/path/to/stretch
```

Without that property, a sibling folder named `stretch` is used. Gradle runs on JDK 25 (`JAVA_HOME`).

```
./gradlew check                     # tests
./gradlew check -Pslow              # also the smoke tier, which needs a GPU
./gradlew :app:run                  # the app
./gradlew :app:createDistributable  # the packaged app, under app/build/compose/binaries/main/app/Squash
```

IntelliJ IDEA: open the folder; the Squash, Check and Smoke run configurations are included, and Stretch shows up as an included build.
