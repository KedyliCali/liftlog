// Hand-drawn outlines for the body map (viewBox 0 0 200 480, centre line x = 100).
// Every path is the figure's half on the viewer's left; BodyMap mirrors it for the other half, so both
// halves stay symmetric and share one data-muscle. Silhouette halves cross the centre line by half a unit
// so the two copies overlap instead of leaving a hairline seam.
import type { BodySide, MapMuscle } from '../lib/muscles'

export const BODY_VIEWBOX = '0 0 200 480'

/** Rounded rectangle, for the six-pack blocks. */
function block(x: number, y: number, width: number, height: number, radius: number): string {
  const right = x + width
  const bottom = y + height
  return `M${x + radius} ${y}H${right - radius}Q${right} ${y} ${right} ${y + radius}V${bottom - radius}Q${right} ${bottom} ${right - radius} ${bottom}H${x + radius}Q${x} ${bottom} ${x} ${bottom - radius}V${y + radius}Q${x} ${y} ${x + radius} ${y}Z`
}

/** Neutral body (head, hands, feet, joints show through between muscles). */
const SILHOUETTE = [
  // head
  'M100.5 8C90 8 81.5 15 81.5 30C81.5 40 83.5 48 87.5 53.5C91 57.5 95.5 60 100.5 60Z',
  // ear
  'M83 29C79 28.5 78 38 82 41.5Z',
  // neck
  'M100.5 50H88.5C88.5 60 87.5 70 85 78L100.5 84Z',
  // torso
  'M100.5 74H87C79 80 66 80 56 83C52 96 56 116 62 130C64 150 66 170 66.5 188C67 200 63 214 60 226C60 236 64 244 70 248L100.5 254Z',
  // upper arm with the shoulder cap
  'M58 82C44 84 35 96 35 114C34 136 33 162 33 188L53 193C56 170 60 150 61.5 132C62 112 62 96 58 82Z',
  // forearm
  'M33 186C27 198 23.5 214 23.5 232C23.5 240 24 248 25 254L35.5 256C38.5 240 44 222 49.5 207C51.5 201 53 196 53 192Z',
  // hand
  'M25 251C19.5 260 18 274 20.5 287C22.5 294 29.5 295 32 289C33.5 284 34 277 33.5 271C36 268.5 38 264 37 259.5L35.5 254Z',
  // thigh
  'M60 222C52 244 48.5 278 51.5 312C53 326 56.5 338 59 350H86.5C87.5 328 91.5 300 95.5 276C97.5 264 99.5 256 100.5 250L70 240Z',
  // lower leg
  'M59 344C55 366 54 392 59 416C61 430 63.5 440 64.5 448H79C80 438 84 424 87 406C91 386 90.5 364 86.5 344Z',
  // foot
  'M65 444C62 452 58 462 58 468C60 472 74 473 82 471C83 462 81 452 79 444Z',
]

const SIDE_DELT = 'M55 83.5C44 85.5 36.5 96 36.5 113C36.5 121 38 127 40 131.5C43 118 47 102 55 83.5Z'
const FOREARM = [
  // brachioradialis and extensors, the bulge on the thumb side
  'M34 191C28.5 202 25 216 25 232C25 240 25.5 246 26 251L30 252C31 236 35 216 42.5 197C40 193 37 191 34 191Z',
  // flexors
  'M43.5 197.5C36.5 214 32.5 234 31 252L34.5 253.5C37.5 238 43 222 48.5 207C50.5 202 52 198 52 194.5C49 193 46 194 43.5 197.5Z',
]

const FRONT: Partial<Record<MapMuscle, string[]>> = {
  TRAPS: ['M88.5 63C86.5 72 78 78.5 63 82C73 84.5 83 82.5 90.5 78.5Z'],
  SIDE_DELTS: [SIDE_DELT],
  FRONT_DELTS: ['M57.5 84C49 100 44 116 41.5 132.5C46.5 136 52.5 132 56.5 126C60 118 62.5 108 63.5 98C63 91 61 86.5 57.5 84Z'],
  CHEST: ['M99 90.5V136C94 144 84 146.5 76 144.5C68 142.5 62.5 134 60.5 124.5C61 112 62 100 64.5 92.5C72 88.5 88 88.5 99 90.5Z'],
  BICEPS: ['M57 135C50 135 42.5 139 39.5 147C36.5 161 37.5 176 41.5 186C45 189 49.5 188.5 52 184.5C55 168 58 150 57 135Z'],
  FOREARMS: FOREARM,
  ABS: [
    block(87.5, 146.5, 11.5, 17.5, 3.5),
    block(86.5, 166, 12.5, 17.5, 3.5),
    block(86.5, 185.5, 12.5, 17.5, 3.5),
    'M99 205.5V238C96 240 92 236 89.5 230C87.5 223 86.5 214 86.5 205.5Z',
  ],
  OBLIQUES: [
    // serratus fingers on the ribs, drawn with the obliques they interlock with
    'M64 139.5C67.5 138.5 71.5 139.5 75 141.5L73.5 146.5C70.5 145.5 67 145.5 64.5 146Z',
    'M64.8 149C68.5 148 72.5 149 76 151L74.5 156C71.5 155 68 155 65.3 155.5Z',
    // external oblique
    'M84.5 152C77 152.5 70.5 156 66 160.5C66.5 174 66.5 186 66.5 194C69.5 204 76 214 84.5 224C84 206 83 182 84.5 152Z',
  ],
  QUADS: [
    // vastus lateralis, the outer sweep
    'M61 232C54.5 252 51.5 282 54 310C55.5 325 58.5 337 62 345C63.5 330 64.5 300 66 270C66.5 256 66 244 61 232Z',
    // rectus femoris down the middle
    'M66.5 236C71.5 238 80 244 87 254C85 278 81.5 306 78.5 333C74.5 337.5 70.5 339 67 337.5C67 312 67.5 286 68.5 262C68.8 252 68 243 66.5 236Z',
    // vastus medialis, the teardrop above the knee
    'M86.5 296C90.5 308 91.5 326 88 338C85 346 77 348 72 344C75 341 78.5 338 80.5 335C82.5 322 84.5 308 86.5 296Z',
    // adductors (folded into the quads on the map)
    'M98 254C96.5 268 93.5 282 88.5 294C87.5 284 88 272 89.5 258C92 255 95 254 98 254Z',
  ],
  CALVES: [
    'M59.5 352C55 368 55 388 59 404C61 398 63 388 64 376C64.5 368 64 358 62 352Z',
    'M84.5 350C91 364 92 384 88 402C85 404 82 400 80.5 394C79.5 380 79.5 364 81.5 352Z',
  ],
}

const BACK: Partial<Record<MapMuscle, string[]>> = {
  TRAPS: ['M99 52H92C90 64 86 74 78 78C70 81 62 82 56.5 85C68 92 82 108 90 128C94 142 97 156 99 168Z'],
  SIDE_DELTS: [SIDE_DELT],
  REAR_DELTS: ['M57 86.5C49 98 44 114 41.5 132C47 134 54 128 59 120C64 112 66 102 66 94.5C63 90 60 87 57 86.5Z'],
  TRICEPS: [
    // lateral head
    'M38.5 122C34.5 142 34.5 166 37.5 186L46 188C47 172 50 154 52 140Z',
    // long head
    'M52.5 140.5C50.5 154 47.5 172 46.5 188.5L53 190.5C56 172 59 154 60.5 134.5C57.5 132.5 54.5 134.5 52.5 140.5Z',
  ],
  FOREARMS: FOREARM,
  LATS: ['M63.5 124C71 126 81 130 88.5 134C93 146 96.5 158 98.5 168C93 184 86 200 78 214C74 212 71 208 68.5 202C67.5 184 65.5 150 63.5 124Z'],
  LOWER_BACK: ['M99 172C95.5 186 90.5 200 84.5 212C82.5 219 82.5 225 84 230H99Z'],
  GLUTES: ['M99 230C86 226 70 226 61.5 234C55.5 246 55.5 262 61.5 272C69.5 280 86 282 99 276Z'],
  HAMSTRINGS: [
    // biceps femoris
    'M56 273C52.5 292 52.5 318 59 343H72C72 320 72 298 73.5 284C67.5 282.5 60.5 278.5 56 273Z',
    // semitendinosus
    'M76 285C76 300 75.5 322 74.5 343H87.5C91.5 320 95 298 97.5 279C90.5 283.5 82 285.5 76 285Z',
  ],
  CALVES: [
    'M61 352C54.5 368 54.5 390 63.5 410C67.5 408 70.5 402 71.5 394V358Z',
    'M73.5 358V394C74.5 404 78.5 410 82.5 412C90.5 394 90.5 370 85.5 352Z',
  ],
}

export const MUSCLE_SHAPES: Record<BodySide, Partial<Record<MapMuscle, string[]>>> = { front: FRONT, back: BACK }
export const SILHOUETTE_SHAPES = SILHOUETTE
