// 진공관 라디오: a boxy wooden valve radio with a cloth speaker and a dial strip; on a table or chest top (y = 0)
def z = -0.5
ai.addRoundBox('#7a4a26', -0.24, 0, z - 0.12, 0.48, 0.26, 0.24, 0.025)    // cabinet
ai.addPart('#c8b48c', -0.2, 0.1, z - 0.125, 0.22, 0.13, 0.008)          // speaker cloth
for (int i = 0; i < 4; i++) {
    ai.addPart('#5a3a22', -0.2, 0.115 + i * 0.03, z - 0.13, 0.22, 0.008, 0.006)   // grille bars
}
ai.addPart('#f2e2b0', 0.04, 0.16, z - 0.125, 0.16, 0.06, 0.006)          // dial window
ai.addPart('#a8322a', 0.1, 0.165, z - 0.13, 0.004, 0.05, 0.004)         // needle
ai.addSphere('#2a2a2a', 0.07, 0.06, z - 0.12, 0.022)                     // knobs
ai.addSphere('#2a2a2a', 0.17, 0.06, z - 0.12, 0.022)
ai.addPart('#e8c84a', -0.2, 0.03, z - 0.125, 0.06, 0.02, 0.004)          // brand badge
