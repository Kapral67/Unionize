package com.maxkapral.tests;

import com.maxkapral.annotations.Unionize;
import java.util.UUID;

@Unionize(types = { UUID.class, String.class, Integer.class },
          names = { "uuid", "str", "num"},
          value = "id")
interface Identifier {
}
