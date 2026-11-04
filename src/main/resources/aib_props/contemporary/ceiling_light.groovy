// flat LED panel on the ceiling: 1.9 x 0.95, its top at y = 1 (the ceiling above its block)
def w = 1.9
def d = 0.95
ai.addPart('#d8d8d8', -w / 2, 0.95, -d, w, 0.05, d)                     // frame
ai.addPart('#fbfbf5', -w / 2 + 0.04, 0.945, -d + 0.04, w - 0.08, 0.006, d - 0.08)  // glowing diffuser
ai.addPart('#eeeeea', -w / 2 + 0.04, 0.944, -d / 2 - 0.004, w - 0.08, 0.002, 0.008)  // seam across the middle
