
OverMapped - Allows a project to map methods, fields, or classes to new names.

  Inspired by Java's inability to have two methods with same name but unique
  return types at compile-time.

A list of parameters follows in the format of 'pom parameter' - 'property'. A
property is used as a command parameter, like `-Dmapping.maps=./maps.yml',
while the parameter name is used in the pom's plugin-configuration.

  maps - mapping.maps
    This is yaml file containing mapping changes. The top-level structure
    should be a list of relational arrays, or a single relational array. Each
    relational array can have three processed keys, `members', `classes', and
    `flags'. Relational arrays are processed in order of appearance. In each
    relational array, sections are processed in the order of `classes', then
    `members', then `flags'. Each consecutive entry (as the set of previously
    described keys) is processed as if every entry is being applied on top of
    any previous entry.

    A member entry follows a format of fully qualified name (delimited by
    slashes or `/' ), the previous name, and a descriptor, appended with
    spaces between in a string literal as the relational key. Methods are
    defined using descriptors that include parenthesis, parameter types, and
    return. Fields are defined using descriptors that include the type. The
    relational value is the new name for the member.

    A method that has a primary definition in multiple super-class or parent-
    interfaces should use a list of fully qualified class name string literals
    as the relational key to a relational table that includes:

      name: the previous name
      map: the new name
      description: the descriptor for the member

    An example of mapping the common Runnable.run() to Runnable.go():

    members:
      "java/lang/Runnable run ()V": go

    An example mapping both Runnable.run() and Thread.run()
    * Note, in a real application, mapping Runnable.run() would automatically
      map Thread.run() as well, because Thread implements/extends Runnable.
      This would not work if the only definition was Thread.run(), as maps are
      only applied to classes that extend or implement the method's class.

    members:
      [ java/lang/Runnable, java/lang/Thread ]:
        name: run
        map: go
        description: "()V"

    Classes are mapped simply by making the previous name a relational key
    with the new name the relational value. An example mapping Runnable to
    GoTime:

    classes:
      "java/lang/Runnable": "java/lang/GoTime"

    Flags are changed by using the fully qualified description (see above) as
    the relational key, with an integer as the relational value. These must be
    exact matches to the members. An example flagging Thread.run() as
    synthetic (this doesn't affect sub-classes!):

    flags:
      "java/lang/Thread run ()V": 1001

    Required

  input - mapping.input
    This is the jar (or zip) file to read from.

    Required

  output - mapping.output
    This is the jar file to write to.

    Default - the input file

  original - mapping.original
    The file to copy the original input to, intended for usage when
    overwriting the input file.

    Default - does not copy the original

  cores - mapping.cores
    The number of cores to use. This must be at least 1 (intended to start no
    extra threads). Extra threads are used to process classes and perform some
    file-IO.

    Default - 2

  missing - mapping.missing
    This value dictates behavior when a class or member specified in the maps
    is not found. Valid options include:

    IGNORE - takes no action.
    WARN - outputs a message in the log.
    FAIL - throws an exception, causing the goal to enter a 'failed' state.
    VERBOSE - outputs detailed information when reading in classes and state
      information when a value is not found. This information is helpful for
      users to debug their maps.

    Default - WARN
