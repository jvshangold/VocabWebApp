package driver

extension [U](u: U)
  inline def assertInstanceOf[V]: V =
    assert(u.isInstanceOf[V], f"$u has unexpected type ${u.getClass}")
    u.asInstanceOf[V]
