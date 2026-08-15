# Tuatara Turing Machine
Tuatarata Turing Machine is a Java program used for the simulation of
finite-state auttomata. Currently the simulation of both deterministic and
nondeterministic Turing Machines and finite-state acceptors is supported. This
is a fork of the original Tuatara Turing Machine written by Jimmy Foulds, which
patches issues present in the original release, adds new features, and provides
a more generic framework to allow the easy addition of new functionality.

## Getting Started
Precompiled releases in the form of JAR archives are available under *Releases*.
Running Tuatara Turing Machine requires the Java JRE (version 8 or later). To
run Tuatara, either double-click the JAR archive, or run 

`java -jar TuataraTuringMachine.jar` 

from the command line. To compile Tuatara Turing Machine, the following tools
are needed:
* `git`
* `make`
* `javac`

To compile, clone the repo and run `make`:

```
git clone https://github.com/354ika/TuataraTuringMachine.git
cd TuataraTuringMachine
make
```

This will compile the project into .class files. To produce a JAR archive, instead run:

`make jar`

This will produce a JAR archive in the build directory.

JavaDoc documentation can also be produced by running:

`make docs`

The checks for the assistant support run with:

`make test`

## Using it with an assistant

Tuatara can let Claude read the machines you have open, correct them, run inputs
and drive the simulation on screen. It is on by default, listening only on
`127.0.0.1` and answering nothing without a token it writes to `~/.tuatara/`.
The status bar says when it is on, and *Configuration → Assistant access* turns
it off. Starting with `-Dtuatara.agent=off` disables it entirely.

To connect Claude Code, use *Configuration → Copy assistant setup command*, or
run:

```
claude mcp add tuatara -- java -jar /path/to/TuataraTuringMachine.jar --mcp
```

The same archive, started with `--mcp`, speaks the protocol on its own input and
output and forwards to whichever window is running — starting one if there is
none. No window, no access.

**What it can do.** Read any open machine and say what is wrong with it, run
Validate, build and edit machines, arrange them, run batches of inputs off-screen
at around twenty-five million steps a second, drive the on-screen simulation, set
the tape, change the settings, and save and open files.

**What it will not do.** Every edit is a single entry on the undo stack, named
after who made it, so one Ctrl+Z reverses anything. States it adds are placed in
free space and nothing you positioned yourself is moved. Rearranging a diagram
you laid out by hand is *offered* — a strip appears at the top of the tab with
Preview, Apply and Dismiss — rather than done. It cannot drive your undo stack,
and it works in its own private drafts until it has something worth showing you.

## Authors
* **Jimmy Foulds** - Initial design and implementation of Tuatara Turing Machine
* **Mitchell Grout** - Redesign and rewrite of existing code, extended functionality
* **Justin Bedggood** - Redesign of all sprites used in the program
* **OfficialProtonDev** (with Claude) - Modernised the user interface: replaced the
  floating-window MDI with tabbed machines, added a themed light/dark design system
  and vector iconography, regrouped the toolbar and menus, added a status bar and
  welcome screen, and redrew the machine canvas, tape and console. Added assistant
  support: an MCP server built into the program, with a layout engine, an
  off-screen simulator, and a consent model for edits to somebody else's diagram

* **354ika** - Adding the speed toggle functionality
