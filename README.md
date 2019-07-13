## multi-vehicle-orienteering

This repository contains a solver written in Kotlin to solve the multi-vehicle
orienteering problem using the branch and price approach.

## Dependencies

- CPLEX
- Kotlin Logging library
- gradle (installation optional, wrapper included)

## Usage

Clone the repository. If not already present, create the file
"~/.gradle/gradle.properties". Add the following two lines to the file:

```
cplexJarPath=/file/path/to/cplex/jar
cplexLibPath=/folder/path/to/cplex/library
```

The key `cplexJarPath` specifies the complete path to the CPLEX JAR file. It
must end with `cplex.jar`. The `cplexLibPath` should point to the path that
holds the CPLEX library (file with dll/a/so extension).  This path should be a
folder path and NOT a file path. It usually points to a folder within the CPLEX
bin folder. For example, the paths could look like so on a Mac:

```
cplexJarPath=/Applications/CPLEX_Studio128/cplex/lib/cplex.jar
cplexLibPath=/Applications/CPLEX_Studio128/cplex/bin/x86-64_osx
```

Open a terminal and make the `./gradlew` file executable if necessary.  Run
`./gradlew build` to compile and `./gradlew run` to run the code.  The latter
task can also be run directly. Another useful task is `./gradlew clean`, which
cleans the gradle build files. If Gradle is installed, `./gradlew` can be
replaced with `gradle` in all the above commands.

## Running with Intellij

- Open Intellij, select "Import Project" and point it to the repo path.
- Select "Import Project from external model -> Gradle".
- Check the following checkboxes:
    + "Use auto-import"
    + "Create directories for empty content roots automatically"
- Select the "Use default gradle wrapper"
- Clock on the "Finish" button on the bottom right.

That's it, nothing else is needed. Ignore complaints about modules that were
removed. To run the project for the first time, select
"View -> Tool Windows -> Gradle". The Gradle window should open up on the
right. Expand the tree and double-click on "Tasks -> application -> run". For
subsequent runs, we can continue doing the same thing, or select this
configuration from the "Run" menu.  Debugging can also be done with this
configuration from the same menu.  After the first run, this task will show up
as something like "Run project-name [run]" and "Debug project-name [run]" in
the "Run" menu, where "project-name" is the name of the project.

## Additional notes

CPLEX and other dependencies have been set up correctly in "build.gradle".
In case some dependencies need access to config files, the files can be placed
in "src/main/resources". This folder already holds "log4j2.xml", the config
file for the "log4j" logging utility.

