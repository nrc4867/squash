// A plain mannequin: a standing biped drawn from a height plan, tube limbs with front and back profiles, a neck, a
// round head, template hands and feet, and a tee. Run it with `tools/recipe.sh kit kit/examples/mannequin.ts`.
import {
  bodyPlan,
  bothSides,
  cover,
  garment,
  heightMarks,
  joint,
  lengthSpan,
  outfit,
  pair,
  roundStations,
  palette,
  recipe,
  socket,
  tube,
  tubePart,
  vToward,
  wardrobe,
  type RecipePart,
} from "squash-kit";

/** Heights above the ground and half widths, in body units. */
export const plan = bodyPlan(
  {
    ankle: 0.045,
    calf: 0.31,
    knee: 0.47,
    hip: 0.84,
    crotch: 0.78,
    waist: 1.06,
    chest: 1.26,
    shoulder: 1.39,
    torsoTop: 1.48,
    headSeat: 1.585,
    neckTop: 1.6,
  },
  { shoulders: 0.175, waist: 0.13, hips: 0.16 },
);

const colors = palette({
  skin: "#d8b48e",
  shirt: "#4f7fa8",
  nails: "#c9a07c",
  ink: [0.11, 0.09, 0.09],
});

const torso = plan.upright("crotch", "torsoTop");
const neck = plan.upright("torsoTop", "neckTop");
const leg = plan.hanging("hip", "ankle");
const arm = lengthSpan(0.57);
const HEAD_HEIGHT = 0.25;
const head = roundStations(HEAD_HEIGHT, 9, 0.55);

/** Where the torso's stations sit, as heights: seat, hips, belly, waist, ribs, chest, upper chest, shoulder, shoulder top. */
const torsoStations = [0.8, 0.88, 0.97, plan.h("waist"), 1.16, plan.h("chest"), 1.36, 1.42, 1.46];
const legStations = [plan.h("hip"), 0.76, 0.66, 0.56, 0.5, plan.h("knee"), 0.41, plan.h("calf"), 0.2, 0.12, 0.085];
const armStations = [0, 0.06, 0.15, 0.24, 0.28, 0.33, 0.42, 0.5, 0.54];

const SHOULDER_SOCKET = 2;
const HIP_SOCKET = 3;
const NECK_SOCKET = 1;
const END_SOCKET = 1;

const parts: RecipePart[] = [
  tubePart(
    {
      id: "torso",
      role: "body",
      color: colors.slot("skin"),
      sockets: [
        socket(NECK_SOCKET, 1, vToward("up", "forward")),
        socket(SHOULDER_SOCKET, torso.u(plan.h("shoulder")), vToward("up", "left"), { mirrored: true }),
        socket(HIP_SOCKET, torso.u(plan.h("hip")), vToward("up", "left"), { mirrored: true }),
      ],
      joints: [
        joint(1, torso.u(0.98), { low: -25, high: 25 }),
        joint(2, torso.u(plan.h("waist") + 0.08), { low: -25, high: 25 }),
        joint(3, torso.u(plan.h("chest")), { low: -25, high: 25 }),
      ],
    },
    {
      rest: "up",
      length: torso.length,
      closed: true,
      at: torsoStations.map(torso.x),
      profiles: {
        front: [0.07, 0.095, 0.1, 0.09, 0.1, 0.112, 0.1, 0.085, 0.06],
        back: [0.085, 0.11, 0.1, 0.082, 0.09, 0.1, 0.095, 0.085, 0.06],
        left: [0.11, plan.w("hips"), 0.15, plan.w("waist"), 0.145, 0.16, plan.w("shoulders"), 0.15, 0.085],
      },
    },
  ),
  tubePart(
    {
      id: "neck",
      on: "torso",
      socket: `socket.${NECK_SOCKET}`,
      role: "neck",
      color: colors.slot("skin"),
      inkJoin: false,
      sockets: [socket(END_SOCKET, neck.u(plan.h("headSeat")), vToward("up", "forward"), { sink: 0 })],
    },
    {
      rest: "up",
      length: neck.length,
      profiles: { front: [0.052, 0.048, 0.047], back: [0.058, 0.052, 0.05], left: [0.056, 0.05, 0.049] },
    },
  ),
  {
    id: "head",
    template: "head-round",
    on: "neck",
    socket: `socket.${END_SOCKET}`,
    role: "head",
    color: colors.slot("skin"),
    blend: 0.03,
    inkJoin: false,
    outlines: tube({
      rest: "up",
      length: HEAD_HEIGHT,
      closed: true,
      at: head.at,
      profiles: { front: head.radii(0.098), back: head.radii(0.108), left: head.radii(0.088) },
    }),
  },
  tubePart(
    {
      id: "arms",
      on: "torso",
      socket: `socket.${SHOULDER_SOCKET}`,
      ...pair("arm"),
      role: "arm",
      outward: 45,
      color: colors.slot("skin"),
      inkJoin: false,
      sockets: [socket(END_SOCKET, 1, vToward("down", "back"))],
      joints: [
        joint(1, arm.u(0.035), { low: -170, high: 60 }),
        joint(2, arm.u(0.28), { low: -145, high: 0, stretch: true }),
      ],
    },
    {
      rest: "down",
      length: arm.length,
      at: armStations,
      profiles: {
        front: [0.05, 0.049, 0.045, 0.04, 0.036, 0.038, 0.034, 0.027, 0.025],
        back: [0.054, 0.052, 0.048, 0.042, 0.038, 0.04, 0.034, 0.028, 0.026],
        outer: [0.058, 0.055, 0.046, 0.04, 0.037, 0.04, 0.036, 0.03, 0.028],
        inner: [0.05, 0.048, 0.044, 0.038, 0.035, 0.038, 0.033, 0.028, 0.026],
      },
    },
  ),
  tubePart(
    {
      id: "legs",
      on: "torso",
      socket: `socket.${HIP_SOCKET}`,
      ...pair("leg"),
      role: "leg",
      outward: 1,
      color: colors.slot("skin"),
      inkJoin: false,
      sockets: [socket(END_SOCKET, 1, vToward("down", "back"))],
      joints: [joint(1, leg.u(plan.h("knee")), { low: 0, high: 140, stretch: true })],
    },
    {
      rest: "down",
      length: leg.length,
      at: legStations.map(leg.x),
      profiles: {
        front: [0.085, 0.08, 0.072, 0.062, 0.053, 0.05, 0.048, 0.045, 0.036, 0.03, 0.029],
        back: [0.095, 0.09, 0.075, 0.062, 0.053, 0.05, 0.058, 0.062, 0.045, 0.032, 0.029],
        outer: [0.09, 0.085, 0.075, 0.064, 0.055, 0.052, 0.054, 0.055, 0.042, 0.032, 0.03],
        inner: [0.07, 0.075, 0.068, 0.058, 0.05, 0.048, 0.05, 0.05, 0.04, 0.03, 0.029],
      },
    },
  ),
  {
    id: "hands",
    template: "hand",
    on: "arms",
    socket: `socket.${END_SOCKET}`,
    ...pair("hand"),
    role: "hand",
    size: 0.85,
    outward: 45,
    inkJoin: false,
    color: colors.slot("skin"),
  },
  {
    id: "feet",
    template: "foot",
    on: "legs",
    socket: `socket.${END_SOCKET}`,
    ...pair("foot"),
    role: "foot",
    size: 0.95,
    inkJoin: false,
    color: colors.slot("skin"),
  },
];

const tee = garment({
  id: "tee",
  slot: "top",
  color: colors.slot("shirt"),
  fit: 0.012,
  covers: [cover("torso", { from: torso.u(0.9), to: 0.96 }), cover("arm-l", { to: arm.u(0.17) })],
});

/** Where the probe finds each planned height: `tools/recipe.sh kit heights` prints planned against built. */
export const heights = heightMarks(plan, {
  hip: { part: "leg-l", joint: "base" },
  knee: { part: "leg-l", joint: "joint.1" },
  ankle: { part: "leg-l", joint: "tip" },
  shoulder: { part: "arm-l", joint: "base" },
  torsoTop: { part: "torso", joint: "tip" },
  headSeat: { part: "neck", socket: "socket.1" },
});

export default recipe({
  id: "mannequin",
  name: "Mannequin",
  plan: "blank",
  "x-features": "plain standing mannequin; seven and a bit heads tall; smooth egg head; tee",
  palette: colors.slots,
  parts,
  wardrobe: wardrobe([tee], [outfit("casual", { top: "tee" })]),
  displayPose: bothSides("arm", { joint: "base", lean: -28 }),
});
