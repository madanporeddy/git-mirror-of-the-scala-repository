object Bug {
  def main(args: Array[String]) {
    val a = new Array[Array[Int]](2,2)
    test(a)
  }
  def test[A](t: Array[Array[A]]) {
    val tmp = t(0)
    t(1) = tmp
  }
}
