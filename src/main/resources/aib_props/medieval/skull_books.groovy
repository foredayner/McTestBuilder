// a stack of old books with a skull and a burning candle on them; on a table or shelf top (y = 0); a little light
def z = -0.5
ai.addRoundBox('#5a2a1e', -0.18, 0, z - 0.14, 0.34, 0.06, 0.26, 0.01)     // books
ai.addRoundBox('#2a3a5a', -0.16, 0.06, z - 0.12, 0.3, 0.05, 0.24, 0.01)
ai.addRoundBox('#3a4a2a', -0.17, 0.11, z - 0.13, 0.28, 0.06, 0.25, 0.01)
ai.addPart('#e8dcc0', -0.165, 0.005, z - 0.145, 0.33, 0.05, 0.004)        // page edges
ai.addEllipsoid('#e8e0c8', 0, 0.25, z, 0.08, 0.075, 0.09)                 // skull
ai.addEllipsoid('#e8e0c8', 0, 0.2, z - 0.05, 0.055, 0.04, 0.05)           // jaw
ai.mirrorOn()
ai.addSphere('#1a1a1a', 0.03, 0.25, z - 0.075, 0.02)                      // eye sockets
ai.mirrorOff()
ai.addPart('#1a1a1a', -0.008, 0.21, z - 0.09, 0.016, 0.02, 0.006)        // nose
ai.addCylinder('#efe6cc', 0.14, 0.17, z + 0.06, 0.02, 0.1)                // candle
ai.addEllipsoid('#efe6cc', 0.14, 0.17, z + 0.06, 0.04, 0.012, 0.04)       // its melted wax
ai.addSphere('#ffc04a', 0.14, 0.29, z + 0.06, 0.012)
