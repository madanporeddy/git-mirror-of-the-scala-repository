/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$

package scala.tools.nsc

import java.io.File
import Settings._

class Settings(errorFn: String => Unit) extends ScalacSettings {
  def this() = this(Console.println)

  // optionizes a system property
  private def sysprop(name: String): Option[String] = onull(System.getProperty(name))

  // given any number of possible path segments, flattens down to a 
  // :-separated style path
  private def concatPath(segments: Option[String]*): String =
    segments.toList.flatMap(x => x) mkString File.pathSeparator

  protected def classpathDefault = 
    sysprop("env.classpath") orElse sysprop("java.class.path") getOrElse ""

  protected def bootclasspathDefault =
    concatPath(sysprop("sun.boot.class.path"), guessedScalaBootClassPath)

  protected def extdirsDefault =
    concatPath(sysprop("java.ext.dirs"), guessedScalaExtDirs)

  protected def pluginsDirDefault = 
    guess(List("misc", "scala-devel", "plugins"), _.isDirectory) getOrElse ""

  def onull[T <: AnyRef](x: T): Option[T] = if (x eq null) None else Some(x)
  def mkPath(base: String, segments: String*) = new File(base, segments.mkString(File.separator))
  def scalaHome: Option[String] = onull(Properties.scalaHome)

  // examine path relative to scala home and return Some(path) if it meets condition
  private def guess(xs: List[String], cond: (File) => Boolean): Option[String] = {
    if (scalaHome.isEmpty) return None
    val f = mkPath(scalaHome.get, xs: _*)
    if (cond(f)) Some(f.getAbsolutePath) else None
  }

  private def guessedScalaBootClassPath: Option[String] =    
    guess(List("lib", "scala-library.jar"), _.isFile) orElse
    guess(List("classes", "library"), _.isDirectory)

  private def guessedScalaExtDirs: Option[String] =
    guess(List("lib"), _.isDirectory)

  override def hashCode() = allSettings.hashCode
  override def equals(that: Any) = that match {
    case s: Settings  => this.allSettings == s.allSettings
    case _            => false
  }

  def checkDependencies: Boolean = {
    def hasValue(s: Setting, value: String): Boolean = s match {
      case bs: BooleanSetting => bs.value
      case ss: StringSetting  => ss.value == value
      case cs: ChoiceSetting  => cs.value == value
      case _ => "" == value
    }
    
    for (setting <- allSettings ; (dep, value) <- setting.dependency)
      if (!setting.isDefault && !hasValue(dep, value)) {
        errorFn("incomplete option " + setting.name + " (requires " + dep.name + ")")
        return false
      }
      
    true
  }

  /** Try to add additional command line parameters.
   *  Returns unconsumed arguments.
   */
  def parseParams(line: String): List[String] =
    parseParams(line.trim.split("""\s+""").toList)
    
  def parseParams(args: List[String]): List[String] = {
    // verify command exists and call setter
    def tryToSetIfExists(
      cmd: String,
      args: List[String],
      setter: (Setting) => (List[String] => Option[List[String]])
    ): Option[List[String]] =
      lookupSetting(cmd) match {
        case None       => errorFn("Parameter '" + cmd + "' is not recognised by Scalac.") ; None
        case Some(cmd)  => setter(cmd)(args)
      }
    
    // if arg is of form -Xfoo:bar,baz,quux
    def parseColonArg(s: String): Option[List[String]] = {
      val idx = s.findIndexOf(_ == ':')
      val (p, args) = (s.substring(0, idx), s.substring(idx+1).split(",").toList)
      
      // any non-Nil return value means failure and we return s unmodified
      tryToSetIfExists(p, args, (s: Setting) => s.tryToSetColon _)
    }
    // if arg is of form -Dfoo=bar or -Dfoo (name = "-D")
    def isPropertyArg(s: String) = lookupSetting(s.substring(0, 2)) match {
      case Some(x: DefinesSetting)  => true
      case _                        => false
    }
    def parsePropertyArg(s: String): Option[List[String]] = {
      val (p, args) = (s.substring(0, 2), s.substring(2))
      
      tryToSetIfExists(p, List(args), (s: Setting) => s.tryToSetProperty _)
    }
    
    // if arg is of form -Xfoo or -Xfoo bar (name = "-Xfoo")
    def parseNormalArg(p: String, args: List[String]): Option[List[String]] =
      tryToSetIfExists(p, args, (s: Setting) => s.tryToSet _)
            
    def doArgs(args: List[String]): List[String] = {
      if (args.isEmpty) return Nil
      val p = args.head
      if (p == "") return args.tail // it looks like ant passes "" sometimes
      
      if (!p.startsWith("-")) {
        errorFn("Parameter '" + p + "' does not start with '-'.")
        return args
      }
      else if (p == "-") {
        errorFn("'-' is not a valid argument.")
        return args
      }
      
      // we dispatch differently based on the appearance of p:
      // 1) If it has a : it is presumed to be -Xfoo:bar,baz
      // 2) If the first two chars are the name of a command, -Dfoo=bar
      // 3) Otherwise, the whole string should be a command name
      //
      // Internally we use Option[List[String]] to discover error,
      // but the outside expects our arguments back unchanged on failure
      if (p contains ":") parseColonArg(p) match {
        case Some(_)  => args.tail
        case None     => args
      }
      else if (isPropertyArg(p)) parsePropertyArg(p) match {
        case Some(_)  => args.tail
        case None     => args
      }
      else parseNormalArg(p, args.tail) match {
        case Some(xs) => xs
        case None     => args
      }
    }

    doArgs(args)
  }
  
  // checks both name and any available abbreviations
  def lookupSetting(cmd: String): Option[Setting] =
    settingSet.find(x => x.name == cmd || (x.abbreviations contains cmd))

  // The *Setting classes used to be case classes defined inside of Settings.
  // The choice of location was poor because it tied the type of each setting
  // to its enclosing instance, which broke equality, so I moved the class
  // definitions into the companion object.  The one benefit it was getting
  // out of this was using its knowledge of the enclosing instance to add
  // itself to the list of settings in the Setting constructor.  However,
  // this was dicey and not working predictably, as illustrated in the comment
  // in GenericRunnerSettings:
  //
  //   For some reason, "object defines extends Setting(...)"
  //   does not work here.  The object is present but the setting
  //   is not added to allsettings.
  //
  // To capture similar semantics, I created instance methods on setting
  // which call a factory method for the right kind of object and then add
  // the newly constructed instance to allsettings.  The constructors are
  // private to force all creation to go through these methods.
  // 
  // The usage of case classes was becoming problematic (due to custom
  // equality, case class inheritance, and the need to control object
  // creation without a synthetic apply method getting in the way) and
  // it was providing little benefit, so they are no longer cases.
  
  // a wrapper for all Setting creators to keep our list up to date
  // and tell them how to announce errors
  private def add[T <: Setting](s: T): T = {
    s setErrorHandler errorFn
    allsettings += s
    s
  }
  
  /**
   *  The canonical creators for Setting objects.
   */  
  import Function.{ tupled, untupled }
  import Setting._
  
  // A bit too clever, but I haven't found any other way to compose
  // functions with arity 2+ without having to annotate parameter types
  lazy val IntSetting          = untupled(tupled(sint _) andThen add[IntSetting])
  lazy val BooleanSetting      = untupled(tupled(bool _) andThen add[BooleanSetting])
  lazy val StringSetting       = untupled(tupled(str _) andThen add[StringSetting])
  lazy val MultiStringSetting  = untupled(tupled(multi _) andThen add[MultiStringSetting])
  lazy val ChoiceSetting       = untupled(tupled(choice _) andThen add[ChoiceSetting])
  lazy val DebugSetting        = untupled(tupled(sdebug _) andThen add[DebugSetting])
  lazy val PhasesSetting       = untupled(tupled(phase _) andThen add[PhasesSetting])
  lazy val DefinesSetting      = add(defines())
}

object Settings
{
  // basically this is a value which remembers if it's been modified
  trait SettingValue {
    type T <: Any
    protected var v: T
    private var setByUser: Boolean = false
    def isDefault: Boolean = !setByUser
    def value: T = v
    def value_=(arg: T) = { setByUser = true ; v = arg }    
    val choices : List[T] = Nil
  }
  
  // The Setting companion object holds all the factory methods
  object Setting {
    def bool(name: String, descr: String) =
      new BooleanSetting(name, descr)
      
    def str(name: String, arg: String, descr: String, default: String) =
      new StringSetting(name, arg, descr, default)
        
    def sint(name: String, descr: String, default: Int, min: Option[Int], max: Option[Int]) =
      new IntSetting(name, descr, default, min, max)
      
    def multi(name: String, arg: String, descr: String) =
      new MultiStringSetting(name, arg, descr)
      
    def choice(name: String, descr: String, choices: List[String], default: String): ChoiceSetting =
      new ChoiceSetting(name, descr, choices, default)
      
    def sdebug(name: String, descr: String, choices: List[String], default: String, defaultEmpty: String) =
      new DebugSetting(name, descr, choices, default, defaultEmpty)    
    
    def phase(name: String, descr: String) =
      new PhasesSetting(name, descr)
    
    def defines() = new DefinesSetting()
  }
  
  /** A base class for settings of all types.
   *  Subclasses each define a `value' field of the appropriate type.
   */
  abstract class Setting(descr: String) extends Ordered[Setting] with SettingValue {
    /** The name of the option as written on the command line, '-' included. */
    def name: String
    
    /** Error handling function, set after creation by enclosing Settings instance */
    private var _errorFn: String => Unit = _
    private[Settings] def setErrorHandler(e: String => Unit) = _errorFn = e
    def errorFn(msg: String) = _errorFn(msg)
    def errorAndValue[T](msg: String, x: T): T = { errorFn(msg) ; x }

    /** After correct Setting has been selected, tryToSet is called with the
     *  remainder of the command line.  It consumes any applicable arguments and
     *  returns the unconsumed ones.
     */
    private[Settings] def tryToSet(args: List[String]): Option[List[String]]
    
    /** Commands which can take lists of arguments in form -Xfoo:bar,baz override
     *  this method and accept them as a list.  It returns List[String] for
     *  consistency with tryToSet, and should return its incoming arguments
     *  unmodified on failure, and Nil on success.
     */
    private[Settings] def tryToSetColon(args: List[String]): Option[List[String]] =
      errorAndValue("'" + name + "' does not accept multiple arguments", None)
    
    /** Commands which take properties in form -Dfoo=bar or -Dfoo
     */
    private[Settings] def tryToSetProperty(args: List[String]): Option[List[String]] =
      errorAndValue("'" + name + "' does not accept property style arguments", None)
    
    /**
     * Attempt to set from a properties file style property value.
     */
    def tryToSetFromPropertyValue(s : String) {
      tryToSet(s :: Nil)
    }

    /** The syntax defining this setting in a help string */
    private var _helpSyntax = name
    def helpSyntax: String = _helpSyntax
    def withHelpSyntax(s: String): this.type    = { _helpSyntax = s ; this }
    
    /** Abbreviations for this setting */
    private var _abbreviations: List[String] = Nil
    def abbreviations = _abbreviations
    def withAbbreviation(s: String): this.type  = { _abbreviations ++= List(s) ; this }

    /** A description of the purpose of this setting in a help string */
    def helpDescription = descr

    /** A list of Strings which can recreate this setting. */
    def unparse: List[String]

    /** Set to false if option should be shown to IDE. */
    var hiddenToIDE: Boolean = true
    def showToIDE() = hiddenToIDE = false

    /** Optional dependency on another setting */
    protected[Settings] var dependency: Option[(Setting, String)] = None
    def dependsOn(s: Setting, value: String): this.type = { dependency = Some((s, value)); this }
    def dependsOn(s: Setting): this.type = dependsOn(s, "")

    def isStandard: Boolean = !isAdvanced && !isPrivate && name != "-Y"
    def isAdvanced: Boolean = (name startsWith "-X") && name != "-X"
    def isPrivate:  Boolean = (name == "-P") || ((name startsWith "-Y") && name != "-Y")
    
    // Ordered (so we can use TreeSet)
    def compare(that: Setting): Int = name compare that.name
    def compareLists[T <% Ordered[T]](xs: List[T], ys: List[T]) = xs.sort(_ < _) == ys.sort(_ < _)
    
    // Equality
    def eqValues: List[Any] = List(name, value)
    def isEq(other: this.type) = eqValues == other.eqValues    
    override def hashCode() = name.hashCode
    override def equals(that: Any) = that match {
      case x: this.type => this isEq x
      case _            => false
    }
  }

  /** A setting represented by a positive integer */
  class IntSetting private[Settings](
    val name: String,
    val descr: String,
    val default: Int,
    val min: Option[Int],
    val max: Option[Int])
  extends Setting(descr)
  {
    type T = Int
    protected var v = default    
    override def value_=(s: Int) = 
      if (isInputValid(s)) super.value_=(s) else errorMsg
    
    // Validate that min and max are consistent
    (min, max) match {
      case (Some(i), Some(j)) => assert(i <= j)
      case _ => ()
    }

    // Helper to validate an input
    private def isInputValid(k: Int): Boolean = (min, max) match {
      case (Some(i), Some(j)) => (i <= k) && (k <= j)
      case (Some(i), None) => (i <= k)
      case (None, Some(j)) => (k <= j)
      case _ => true
    }

    // Helper to generate a textual explaination of valid inputs
    private def getValidText: String = (min, max) match {
      case (Some(i), Some(j)) => "must be between "+i+" and "+j
      case (Some(i), None)    => "must be greater than or equal to "+i
      case (None, Some(j))    => "must be less than or equal to "+j
      case _                  => throw new Error("this should never be used")
    }

    // Ensure that the default value is actually valid
    assert(isInputValid(default))
    
    def parseInt(x: String): Option[Int] =
      try   { Some(x.toInt) }
      catch { case _: NumberFormatException => None }

    def errorMsg = errorFn("invalid setting for -"+name+" "+getValidText)

    def tryToSet(args: List[String]) = 
      if (args.isEmpty) errorAndValue("missing argument", None)
      else parseInt(args.head) match {
        case Some(i)  => value = i ; Some(args.tail)
        case None     => errorMsg ; None
      }

    def unparse: List[String] =
      if (value == default) Nil
      else List(name, value.toString)
  }

  /** A setting represented by a boolean flag (false, unless set) */
  class BooleanSetting private[Settings](
    val name: String,
    val descr: String)
  extends Setting(descr)
  {
    type T = Boolean
    protected var v = false

    def tryToSet(args: List[String]) = { value = true ; Some(args) }
    def unparse: List[String] = if (value) List(name) else Nil
    override def tryToSetFromPropertyValue(s : String) {
      value = s.equalsIgnoreCase("true")
    }
  }

  /** A setting represented by a string, (`default' unless set) */
  class StringSetting private[Settings](
    val name: String,
    val arg: String,
    val descr: String,
    val default: String)
  extends Setting(descr)
  { 
    type T = String
    protected var v = default

    def tryToSet(args: List[String]) = args match {
      case Nil      => errorAndValue("missing argument", None)
      case x :: xs  => value = x ; Some(xs)
    }    
    def unparse: List[String] = if (value == default) Nil else List(name, value)

    withHelpSyntax(name + " <" + arg + ">")
  }

  /** A setting that accumulates all strings supplied to it */
  class MultiStringSetting private[Settings](
    val name: String,
    val arg: String,
    val descr: String)
  extends Setting(descr)
  {
    type T = List[String]
    protected var v: List[String] = Nil
    def appendToValue(str: String) { value ++= List(str) }
    
    def tryToSet(args: List[String]) = {      
      args foreach appendToValue
      Some(Nil)
    }
    override def tryToSetColon(args: List[String]) = tryToSet(args)
    def unparse: List[String] = value map { name + ":" + _ }
    
    withHelpSyntax(name + ":<" + arg + ">")
  }

  /** A setting represented by a string in a given set of <code>choices</code>,
   *  (<code>default</code> unless set).
   */
  class ChoiceSetting private[Settings](
    val name: String,
    val descr: String,
    override val choices: List[String],
    val default: String)
  extends Setting(descr + choices.mkString(" (", ",", ")"))
  {
    type T = String
    protected var v: String = default
    protected def argument: String = name.substring(1)

    def tryToSet(args: List[String]) = { value = default ; Some(args) }
    override def tryToSetColon(args: List[String]) = args match {
      case Nil                            => errorAndValue("missing " + argument, None)
      case List(x) if choices contains x  => value = x ; Some(Nil)
      case List(x)                        => errorAndValue("'" + x + "' is not a valid choice for '" + name + "'", None)
      case xs                             => errorAndValue("'" + name + "' does not accept multiple arguments.", None)
    }
    def unparse: List[String] =
      if (value == default) Nil else List(name + ":" + value)
      
    withHelpSyntax(name + ":<" + argument + ">")
  }

  /** Same as ChoiceSetting but have a <code>level</code> int which tells the
   *  index of the selected choice. The <code>defaultEmpty</code> is used when
   *  this setting is used without specifying any of the available choices.
   */
  class DebugSetting private[Settings](
    name: String,
    descr: String,
    choices: List[String],
    default: String,
    val defaultEmpty: String)
  extends ChoiceSetting(name, descr, choices, default)
  {
    def indexOf[T](xs: List[T], e: T): Option[Int] = xs.indexOf(e) match {
      case -1 => None
      case x  => Some(x)
    }
    var level: Int = indexOf(choices, default).get

    override def value_=(choice: String) = {
      super.value_=(choice)
      level = indexOf(choices, choice).get
    }

    override def tryToSet(args: List[String]) = 
      if (args.isEmpty) { value = defaultEmpty ; Some(Nil) }
      else super.tryToSet(args)
  }
  
  /** A setting represented by a list of strings which should be prefixes of
   *  phase names. This is not checked here, however.  Alternatively the string
   *  "all" can be used to represent all phases.
   *  (the empty list, unless set)
   */
  class PhasesSetting private[Settings](
    val name: String,
    val descr: String)
  extends Setting(descr + " <phase> or \"all\"")
  {
    type T = List[String]
    protected var v: List[String] = Nil
    
    def tryToSet(args: List[String]) = errorAndValue("missing phase", None)
    override def tryToSetColon(args: List[String]) = args match {
      case Nil  => errorAndValue("missing phase", None)
      case xs   => value ++= xs ; Some(Nil)
    }
    // we slightly abuse the usual meaning of "contains" here by returning
    // true if our phase list contains "all", regardless of the incoming argument
    def contains(phasename: String): Boolean =
      doAllPhases || (value exists { phasename startsWith _ } )

    def doAllPhases() = value contains "all"
    def unparse: List[String] = value map { name + ":" + _ }

    override def equals(that: Any) = that match {
      case ps: PhasesSetting if name == ps.name =>
        (doAllPhases && ps.doAllPhases) || compareLists(value, ps.value)
      case _                                    => false
    }
    
    withHelpSyntax(name + ":<phase>")
  }
  
  /** A setting for a -D style property definition */
  class DefinesSetting private[Settings] extends Setting("set a Java property")
  {
    type T = List[(String, String)]
    protected var v: T = Nil
    def name = "-D"
    withHelpSyntax(name + "<prop>")
    
    // given foo=bar returns Some(foo, bar), or None if parse fails
    def parseArg(s: String): Option[(String, String)] = {
      if (s == "") return None
      val regexp = """^(.*)?=(.*)$""".r
      
      regexp.findAllIn(s).matchData.toList match {
        case Nil      => Some(s, "")
        case List(md) => md.subgroups match { case List(a,b) => Some(a,b) }
      }
    }
        
    def tryToSet(args: List[String]) =    
      if (args.isEmpty) None
      else parseArg(args.head) match {
        case None         => None
        case Some((a, b)) => value ++= List((a, b)) ; Some(args.tail)
      }

    /** Apply the specified properties to the current JVM */
    def applyToCurrentJVM =
      value foreach { case (k, v) => System.getProperties.setProperty(k, v) }
    
    def unparse: List[String] =
      value map { case (k,v) => "-D" + k + (if (v == "") "" else "=" + v) }
  }

}

trait ScalacSettings
{
  self: Settings =>
  
  import collection.immutable.TreeSet
  
  /** A list of all settings */
  protected var allsettings: Set[Setting] = TreeSet[Setting]()
  def settingSet: Set[Setting] = allsettings
  def allSettings: List[Setting] = settingSet.toList
  
  /** Disable a setting */
  def disable(s: Setting) = allsettings -= s
  
  /**
   *  Temporary Settings
   */
  val suppressVTWarn = BooleanSetting    ("-Ysuppress-vt-typer-warnings", "Suppress warnings from the typer when testing the virtual class encoding, NOT FOR FINAL!") 
  
  /** 
   *  Standard settings
   */
  // argfiles is only for the help message
  val argfiles      = BooleanSetting    ("@<file>", "A text file containing compiler arguments (options and source files)")
  val bootclasspath = StringSetting     ("-bootclasspath", "path", "Override location of bootstrap class files", bootclasspathDefault)
  val classpath     = StringSetting     ("-classpath", "path", "Specify where to find user class files", classpathDefault).withAbbreviation("-cp")  
  val outdir        = StringSetting     ("-d", "directory", "Specify where to place generated class files", ".")
  val dependenciesFile  = StringSetting ("-dependencyfile", "file", "Specify the file in which dependencies are tracked", ".scala_dependencies")
  val deprecation   = BooleanSetting    ("-deprecation", "Output source locations where deprecated APIs are used")
  val encoding      = StringSetting     ("-encoding", "encoding", "Specify character encoding used by source files", Properties.encodingString)
  val explaintypes  = BooleanSetting    ("-explaintypes", "Explain type errors in more detail")
  val extdirs       = StringSetting     ("-extdirs", "dirs", "Override location of installed extensions", extdirsDefault)
  val debuginfo     = DebugSetting      ("-g", "Specify level of generated debugging info", List("none", "source", "line", "vars", "notailcalls"), "vars", "vars")
  val help          = BooleanSetting    ("-help", "Print a synopsis of standard options")
  val make          = ChoiceSetting     ("-make", "Specify recompilation detection strategy", List("all", "changed", "immediate", "transitive"), "all") .
                                          withHelpSyntax("-make:<strategy>")
  val nowarnings    = BooleanSetting    ("-nowarn", "Generate no warnings")
  val XO            = BooleanSetting    ("-optimise", "Generates faster bytecode by applying optimisations to the program")
  val printLate     = BooleanSetting    ("-print", "Print program with all Scala-specific features removed")
  val sourcepath    = StringSetting     ("-sourcepath", "path", "Specify where to find input source files", "")
  val target        = ChoiceSetting     ("-target", "Specify for which target object files should be built", List("jvm-1.5", "jvm-1.4", "msil"), "jvm-1.5")
  val unchecked     = BooleanSetting    ("-unchecked", "Enable detailed unchecked warnings")
  val uniqid        = BooleanSetting    ("-uniqid", "Print identifiers with unique names for debugging")
  val verbose       = BooleanSetting    ("-verbose", "Output messages about what the compiler is doing")
  val version       = BooleanSetting    ("-version", "Print product version and exit")
  
  /**
   * -X "Advanced" settings
   */
  val Xhelp         = BooleanSetting    ("-X", "Print a synopsis of advanced options")
  val assemname     = StringSetting     ("-Xassem", "file", "Name of the output assembly (only relevant with -target:msil)", "").dependsOn(target, "msil")
  val assemrefs     = StringSetting     ("-Xassem-path", "path", "List of assemblies referenced by the program (only relevant with -target:msil)", ".").dependsOn(target, "msil")
  val Xchecknull    = BooleanSetting    ("-Xcheck-null", "Emit warning on selection of nullable reference")
  val checkInit     = BooleanSetting    ("-Xcheckinit", "Add runtime checks on field accessors. Uninitialized accesses result in an exception being thrown.")
  val noassertions  = BooleanSetting    ("-Xdisable-assertions", "Generate no assertions and assumptions")
  val Xexperimental = BooleanSetting    ("-Xexperimental", "Enable experimental extensions")
  val future        = BooleanSetting    ("-Xfuture", "Turn on future language features")
  val genPhaseGraph = StringSetting     ("-Xgenerate-phase-graph", "file", "Generate the phase graphs (outputs .dot files) to fileX.dot", "")
  val XlogImplicits = BooleanSetting    ("-Xlog-implicits", "Show more info on why some implicits are not applicable")
  val nouescape     = BooleanSetting    ("-Xno-uescape", "Disables handling of \\u unicode escapes") 
  val XnoVarargsConversion = BooleanSetting("-Xno-varargs-conversion", "disable varags conversion")
  val Xnojline      = BooleanSetting    ("-Xnojline", "Do not use JLine for editing")
  val plugin        = MultiStringSetting("-Xplugin", "file", "Load a plugin from a file")
  val disable       = MultiStringSetting("-Xplugin-disable", "plugin", "Disable a plugin")
  val showPlugins   = BooleanSetting    ("-Xplugin-list", "Print a synopsis of loaded plugins")
  val require       = MultiStringSetting("-Xplugin-require", "plugin", "Abort unless a plugin is available")
  val pluginsDir    = StringSetting     ("-Xpluginsdir", "path", "Location to find compiler plugins", pluginsDirDefault)
  val print         = PhasesSetting     ("-Xprint", "Print out program after")
  val writeICode    = BooleanSetting    ("-Xprint-icode", "Log internal icode to *.icode files")
  val Xprintpos     = BooleanSetting    ("-Xprint-pos", "Print tree positions (as offsets)")
  val printtypes    = BooleanSetting    ("-Xprint-types", "Print tree types (debugging option)")
  val prompt        = BooleanSetting    ("-Xprompt", "Display a prompt after each error (debugging option)")
  val resident      = BooleanSetting    ("-Xresident", "Compiler stays resident, files to compile are read from standard input")
  val script        = StringSetting     ("-Xscript", "object", "Compile as a script, wrapping the code into object.main()", "")
  val Xshowcls      = StringSetting     ("-Xshow-class", "class", "Show class info", "")
  val Xshowobj      = StringSetting     ("-Xshow-object", "object", "Show object info", "")
  val showPhases    = BooleanSetting    ("-Xshow-phases", "Print a synopsis of compiler phases")
  val sourceReader  = StringSetting     ("-Xsource-reader", "classname", "Specify a custom method for reading source files", "scala.tools.nsc.io.SourceReader")
  val Xwarninit     = BooleanSetting    ("-Xwarninit", "Warn about possible changes in initialization semantics")
  
  /**
   * -Y "Private" settings
   */
  val Yhelp         = BooleanSetting    ("-Y", "Print a synopsis of private options")
  val browse        = PhasesSetting     ("-Ybrowse", "Browse the abstract syntax tree after")
  val check         = PhasesSetting     ("-Ycheck", "Check the tree at the end of")
  val Xcloselim     = BooleanSetting    ("-Yclosure-elim", "Perform closure elimination")
  val Xcodebase     = StringSetting     ("-Ycodebase", "codebase", "Specify the URL containing the Scala libraries", "")
  val completion    = BooleanSetting    ("-Ycompletion", "Enable tab-completion in the REPL")
  val Xdce          = BooleanSetting    ("-Ydead-code", "Perform dead code elimination")
  val debug         = BooleanSetting    ("-Ydebug", "Output debugging messages")
  val debugger      = BooleanSetting    ("-Ydebugger", "Enable interactive debugger")
  val Xdetach       = BooleanSetting    ("-Ydetach", "Perform detaching of remote closures")
  // val doc           = BooleanSetting    ("-Ydoc", "Generate documentation")
  val inline        = BooleanSetting    ("-Yinline", "Perform inlining when possible")
  val Xlinearizer   = ChoiceSetting     ("-Ylinearizer", "Linearizer to use", List("normal", "dfs", "rpo", "dump"), "rpo") .
                                          withHelpSyntax("-Ylinearizer:<which>") 
  val log           = PhasesSetting     ("-Ylog", "Log operations in")
  val Ynogenericsig = BooleanSetting    ("-Yno-generic-signatures", "Suppress generation of generic signatures for Java")
  val noimports     = BooleanSetting    ("-Yno-imports", "Compile without any implicit imports")
  val nopredefs     = BooleanSetting    ("-Yno-predefs", "Compile without any implicit predefined values")
  val Yrecursion    = IntSetting        ("-Yrecursion", "Recursion depth used when locking symbols", 0, Some(0), None)
  val selfInAnnots  = BooleanSetting    ("-Yself-in-annots", "Include a \"self\" identifier inside of annotations")
  val Xshowtrees    = BooleanSetting    ("-Yshow-trees", "Show detailed trees when used in connection with -print:phase")
  val skip          = PhasesSetting     ("-Yskip", "Skip")
  val Xsqueeze      = ChoiceSetting     ("-Ysqueeze", "if on, creates compact code in matching", List("on","off"), "on") .
                                          withHelpSyntax("-Ysqueeze:<enabled>") 
  val statistics    = BooleanSetting    ("-Ystatistics", "Print compiler statistics")
  val stop          = PhasesSetting     ("-Ystop", "Stop after phase")
  val refinementMethodDispatch =
                      ChoiceSetting     ("-Ystruct-dispatch", "Selects dispatch method for structural refinement method calls",
                        List("no-cache", "mono-cache", "poly-cache"), "poly-cache") .
                        withHelpSyntax("-Ystruct-dispatch:<method>")
  val Xwarndeadcode = BooleanSetting    ("-Ywarn-dead-code", "Emit warnings for dead code")
  
  /**
   * -P "Plugin" settings
   */
  val pluginOptions = MultiStringSetting("-P", "plugin:opt", "Pass an option to a plugin") .
                        withHelpSyntax("-P:<plugin>:<opt>")
  
  /**
   *  IDE Visible Settings - "showToIDE" called on each.
   */

  val settingsForIDE = {
    val xs = List(
      argfiles, dependenciesFile, debuginfo, make, XO, target,
      Xchecknull, checkInit, noassertions, Xexperimental, future, XlogImplicits, nouescape, XnoVarargsConversion, pluginsDir, Xwarninit,
      Xcloselim, Xdce, Xdetach, inline, Xlinearizer, Ynogenericsig, noimports, nopredefs, selfInAnnots, Xwarndeadcode
    )
    xs foreach { _.showToIDE }
    xs
  }
}
