class Foo {
  object aaa {
    println("a")
  }
}

class Bar extends Foo {
  object b {
    //println("b: " + a) //OK
    println("b: " + Bar.super.aaa)
  }
}

object bug extends Application {
  new Bar
  ()
}
