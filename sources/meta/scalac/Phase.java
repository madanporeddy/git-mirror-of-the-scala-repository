/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

package meta.scalac;

/** A representation for Scala compiler phases. */
public class Phase {

    //########################################################################
    // Public Constants

    public static final Phase
        START         = new Phase("start", null),
        PARSER        = new Phase("parser", "PARSER"),
        DESUGARIZER   = new Phase("desugarizer", "ANALYZER"),
        ANALYZER      = new Phase("analyzer", "ANALYZER"),
	REFCHECK      = new Phase("refcheck", "REFCHECK"),
        UNCURRY       = new Phase("uncurry", "UNCURRY"),
        TRANSMATCH    = new Phase("transmatch", "TRANSMATCH"),
        LAMBDALIFT    = new Phase("lambdalift", "LAMBDALIFT"),
        EXPLICITOUTER = new Phase("explicitouter", "EXPLICITOUTER"),
        ERASURE       = new Phase("erasure", "ERASURE"),
        UNKNOWN       = new Phase("? !!!", null),
        END           = new Phase("-", null);

    //########################################################################
    // Public Fields

    public final String name;
    public final String constant;

    //########################################################################
    // Public Constructors

    public Phase(String name, String constant) {
        this.name = name;
        this.constant = constant;
    }

    //########################################################################
}
