// oil lamp with a glass chimney and a small flame; stands on a table top (y = 0 is the top); it lights the room
def z = -0.5
def brass = '#c9a24a'
ai.addCylinder(brass, 0, 0, z, 0.09, 0.02)                         // foot
ai.addCone(brass, 0, 0.02, z, 0.07, 0.03, 0.06)                    // stem
ai.addSphere('#7a9c84', 0, 0.14, z, 0.075)                         // green glass oil font
ai.addCylinder(brass, 0, 0.2, z, 0.05, 0.03)                       // burner collar
ai.addCylinder(brass, 0, 0.23, z, 0.035, 0.02)
ai.addCylinder('#d8d8d8', 0.05, 0.215, z, 0.008, 0.012)            // wick knob
ai.addEllipsoid('#e8f2f4/50', 0, 0.33, z, 0.045, 0.07, 0.045)      // chimney bulb
ai.addCylinder('#e8f2f4/50', 0, 0.36, z, 0.025, 0.12)              // chimney neck
ai.addSphere('#ffcf5a', 0, 0.29, z, 0.018)                         // flame
ai.addSphere('#fff2b0', 0, 0.3, z, 0.01)
