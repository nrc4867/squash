/** A foot wider than this share of its length is suspect: two feet read as one, or a foot turned to the viewer. */
export const FOOT_WIDTH_OF_LENGTH = 0.6;
/** A hand wider than this share of its length is suspect: a hand read with the other hand or the body, or a fist seen end-on. */
export const HAND_WIDTH_OF_LENGTH = 0.8;
/** Two views' readings of one size, as shares of the head, differ by at most this share of the larger before they disagree. */
export const VIEWS_AGREE = 0.2;
