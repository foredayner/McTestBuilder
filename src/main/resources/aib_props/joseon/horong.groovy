// 호롱불: a small round earthenware oil lamp with a lit wick; stands on a table, chest or shelf top (y = 0); it lights the room
def z = -0.5
ai.addCylinder('#4e3a2a', 0, 0, z, 0.06, 0.012)                 // little base
ai.addEllipsoid('#e9e1cc', 0, 0.07, z, 0.07, 0.06, 0.07)        // the white jar
ai.addCylinder('#e9e1cc', 0, 0.12, z, 0.025, 0.03)              // its neck
ai.addCylinder('#8a7a5a', 0, 0.15, z, 0.012, 0.025)             // wick holder
ai.addSphere('#ffc04a', 0, 0.19, z, 0.016)                      // flame
ai.addSphere('#fff0b0', 0, 0.195, z, 0.008)
ai.addPart('#3a5a8a', -0.05, 0.06, z - 0.068, 0.1, 0.012, 0.004) // a blue painted band
