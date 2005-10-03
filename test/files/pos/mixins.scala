package mixins;
abstract class Super {
  def foo: int;
}
trait Mixin extends Super {
  abstract override def foo = super.foo;
}
trait MixinSub extends Super with Mixin {
  abstract override def foo: int = super.foo;
}
trait MixinSubSub extends MixinSub {
  abstract override def foo = super.foo;
}
class Sub extends Super {
  def foo: int = 1
}
class Base extends Sub with MixinSubSub {
  override def foo = super.foo;
}
trait Mixin1 extends Sub with MixinSubSub {}
class Base1 extends Mixin1 {}

