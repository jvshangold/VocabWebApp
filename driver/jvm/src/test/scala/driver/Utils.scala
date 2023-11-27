package driver

extension [U](u: U)
  inline def assertInstanceOf[V]: V =
    assert(u.isInstanceOf[V])
    u.asInstanceOf[V]
