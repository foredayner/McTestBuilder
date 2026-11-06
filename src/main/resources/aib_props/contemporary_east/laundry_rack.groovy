// 빨래 건조대: a folding drying rack with a few clothes, in the middle of its block
def z = -0.5
def steel = '#d8dadc'
ai.mirrorOn()
ai.addCapsule(steel, 0.4, 0.0, z - 0.3, 0.4, 0.9, z, 0.012)          // X legs at each end
ai.addCapsule(steel, 0.4, 0.0, z + 0.3, 0.4, 0.9, z, 0.012)
ai.mirrorOff()
for (int i = 0; i < 5; i++) {                                       // the drying rails
    def t = -0.2 + i * 0.1
    ai.addCapsule(steel, -0.4, 0.75, z + t, 0.4, 0.75, z + t, 0.007)
}
ai.addCapsule(steel, -0.4, 0.9, z, 0.4, 0.9, z, 0.01)               // top rail
ai.addPart('#f4f1ea', -0.3, 0.45, z - 0.205, 0.26, 0.3, 0.01)       // a white towel
ai.addPart('#5a8ab8', 0.02, 0.5, z - 0.105, 0.22, 0.25, 0.01)       // a blue T-shirt
ai.addPart('#5a8ab8', -0.04, 0.68, z - 0.105, 0.34, 0.07, 0.01)     // its sleeves
ai.addPart('#e8a8b8', -0.15, 0.55, z + 0.095, 0.08, 0.2, 0.008)     // socks
ai.addPart('#3a3a3a', -0.02, 0.58, z + 0.095, 0.08, 0.17, 0.008)
