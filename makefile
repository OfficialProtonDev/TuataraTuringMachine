# Compiler to use
CC=javac

# Bytecode level to target. Without this, javac emits class files for whatever JDK happens to be
# installed, and the resulting archive dies with an UnsupportedClassVersionError on any older JRE --
# including the one Windows typically registers for double-clicking a .jar.
RELEASE=8

# Directory to find source files
SOURCE_DIR=src

# Directory to find test sources
TEST_DIR=test

# Directory to find class files
BUILD_DIR=build

# Directory to find compiled tests. Kept out of BUILD_DIR so the tests never reach the archive.
TEST_BUILD_DIR=build-test

# Directory to find documentation HTML
DOCS_DIR=docs

# Directory to find images
IMG_DIR=images

# Directory to find HTML files
HTML_DIR=help

# Name of the main file
MAIN_FILE=tuataraTMSim.MainWindow

# Name of the .JAR file
FILE_JAR=TuataraTuringMachine.jar

# Separator between classpath entries. Windows uses a semicolon; everything else a colon.
ifeq ($(OS),Windows_NT)
CPSEP=;
else
CPSEP=:
endif


# Default behaviour for make
all: gui

# Compile everything such that the GUI can be run, but do not archive.
gui:
	mkdir -p $(BUILD_DIR)
	$(CC) --release $(RELEASE) -Xlint:-options -d $(BUILD_DIR) `find $(SOURCE_DIR) -name "*.java"`
	cp -r $(SOURCE_DIR)/tuataraTMSim/$(IMG_DIR) $(BUILD_DIR)/tuataraTMSim/$(IMG_DIR)
	cp -r $(SOURCE_DIR)/tuataraTMSim/$(HTML_DIR) $(BUILD_DIR)/tuataraTMSim/$(HTML_DIR)

# Compile everything such that the GUI can be run, and archive the $(BUILD_DIR) directory
jar:
	make gui
	cd $(BUILD_DIR) && jar cvfe $(FILE_JAR) $(MAIN_FILE) `find . -not -name "*.jar"`

# Compile and run the checks for the agent core. These need no display and no window: the layer
# they cover is the one that decides what a machine means, so it is worth testing on its own.
test:
	make gui
	mkdir -p $(TEST_BUILD_DIR)
	$(CC) --release $(RELEASE) -Xlint:-options -cp $(BUILD_DIR) -d $(TEST_BUILD_DIR) \
		`find $(TEST_DIR) -name "*.java"`
	java -Djava.awt.headless=true -cp "$(BUILD_DIR)$(CPSEP)$(TEST_BUILD_DIR)" tuataraTMSim.agent.AgentTest

# Generate only javadoc documentation for the project
docs:
	mkdir -p $(DOCS_DIR)
	javadoc -private -d $(DOCS_DIR) `find $(SOURCE_DIR) -name "*.java"`

# Run the project
run:
	cd $(BUILD_DIR) && java $(MAIN_FILE)

# Compile and run the project
compile-run:
	make gui
	make run

# Remove all .class files, .jar files
.PHONY: clean test
clean:
	rm -rf $(BUILD_DIR) $(TEST_BUILD_DIR)
