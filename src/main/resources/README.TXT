
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

    MEMBERS (Fields / Methods)

    A member entry requires a fully qualified name (delimited by slashes or
    `/' ), the previous name, and a descriptor. Methods will use parenthesis
    around the parameter list, with a trailing return type, to define their
    description. Fields simply use their type as a descriptor. This
    information is interpreted from the structure of the yaml. Member entries
    are contained in a relational array. Each member entry may be a variety
    of styles.

    To map a method similarly-defined in multiple
    classes, where some class may implement / extend said classes, but the
    classes do not extend or implement each other, a multi-class mapping
    needs to be used. The mappings should have a relational key of some
    sequence. Each mapping should have a relational value of the new string-
    name of the member. The relational key can be a relational pair of
    strings, or a single-string with a (single) space-delimitation.

    An example mapping both Runnable.run() and Thread.run()
    * Note, in a real application, mapping Runnable.run() would automatically
      map Thread.run() as well, because Thread implements/extends Runnable.
      This would not work if the only definition was Thread.run(), as maps are
      only applied to classes that extend or implement the method's class.

    members:
      [ java/lang/Runnable, java/lang/Thread ]:
        "run ()V": go

    The above example is equally valid to using a relational pair in this
    example:

    members:
      [ java/lang/Runnable, java/lang/Thread ]:
        { "run": "()V" }: go

    In a real application, it would be appropriate to only define the mapping
    from Runnable, so only providing a single class-name as the relational
    key to the maps is also correct, like in these two examples:

    members:
      java/lang/Runnable:
        "run ()V": go

    members:
      java/lang/Runnable:
        { "run": "()V" }: go

    Similarly, the same styles will also work for fields (only when a
    single-class is provided). These examples demonstrate renaming
    ByteArrayInputStream.buf to buffer:

    members:
      java/io/ByteArrayInputStream:
        "buf [B": buffer

    members:
      java/io/ByteArrayInputStream:
        { buf: "[B" }: buffer

    Specifically for fields, the description may be ommitted if there is only
    a single field with the same name in the class:

    members:
      java/io/ByteArrayInputStream:
        buf: buffer

    members:
      java/io/ByteArrayInputStream:
        { buf: }: buffer

    For situations that multiple fields in a particular style need to be
    renamed, the relational key can be a relational pair of sequence to
    starting name, where the relational value is a sequence of values to map
    each respective entry, starting from the defined value. This style
    follows the same behavior from omitting descriptions, such that all the
    specified names need to be unique.

    This example shows the general syntax:

    members:
      java/package/Class:
        { [ FIELD_1, FIELD_2, FIELD_3, FIELD_4 ]: FIELD_2 }:
        - OTHER_FIELD_2
        - OTHER_FIELD_3

    Assuming the appropriate Class existed, this would map the fields of
    FIELD_2 and FIELD_3 to OTHER_FIELD_2 and OTHER_FIELD_3 respectively. If
    FIELD_1 or FIELD_4 existed, they would not be affected. For repetitive
    uses of these lists, anchors and aliases can be helpful.

    CLASS NAMES

    Classes are mapped simply by making the previous name a relational key
    with the new name the relational value. An example mapping Runnable to
    GoTime:

    classes:
      "java/lang/Runnable": "java/lang/GoTime"

    MEMBER FLAGS

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

  findParents - mapping.findParents
    This value indicates should attempt to detect when a mapped methods loses
    inheritance to a parent's (implemented interface or extended class)
    method. If a parent class has a method with the same signature, OverMapped
    will print a message indicating the method that is no longer overridden.

    Default - false
