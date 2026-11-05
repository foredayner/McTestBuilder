// gramophone with a flaring brass horn; stands on a table top (y = 0 is the table's top), back at z = 0
def z = -0.5
def wood = '#6b3f22'
def dark = '#4a2a16'
def brass = '#c9a24a'
ai.addRoundBox(wood, -0.22, 0, z - 0.22, 0.44, 0.16, 0.44, 0.02)          // cabinet
ai.addPart(dark, -0.225, 0.02, z - 0.225, 0.45, 0.02, 0.45)               // dark band at the bottom
ai.addPart(dark, -0.225, 0.13, z - 0.225, 0.45, 0.015, 0.45)              // and at the top edge
ai.addCylinder('#2a2a2a', -0.04, 0.16, z, 0.17, 0.015)                    // turntable
ai.addCylinder('#111111', -0.04, 0.175, z, 0.155, 0.008)                  // the record
ai.addCylinder('#b33a2a', -0.04, 0.183, z, 0.04, 0.003)                   // its label
ai.addCylinder(brass, -0.04, 0.183, z, 0.006, 0.02)                       // spindle
ai.addCylinder(brass, 0.15, 0.16, z + 0.12, 0.025, 0.06)                  // tone-arm base
ai.addCapsule(brass, 0.15, 0.21, z + 0.12, 0.04, 0.2, z - 0.06, 0.008)    // tone arm
ai.addCylinder(brass, 0.15, 0.21, z + 0.12, 0.02, 0.12)                   // neck up to the horn
ai.addCone(brass, 0.15, 0.33, z + 0.12, 0.03, 0.06, 0.08)                 // horn: a narrow throat...
ai.addCone(brass, 0.15, 0.41, z + 0.12, 0.06, 0.15, 0.12)                 // ...widening...
ai.addCone(brass, 0.15, 0.53, z + 0.12, 0.15, 0.27, 0.08)                 // ...to the bell
ai.addCone('#8e6f2a', 0.15, 0.6, z + 0.12, 0.27, 0.27, 0.012)             // darker rim of the bell
ai.addCapsule(brass, 0.24, 0.08, z - 0.18, 0.3, 0.08, z - 0.18, 0.012)    // winding crank
ai.addSphere(dark, 0.31, 0.08, z - 0.18, 0.02)
