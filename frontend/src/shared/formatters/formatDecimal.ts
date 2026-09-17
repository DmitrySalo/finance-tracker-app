const decimalPattern = /^(-?)(\d+)(?:\.(\d+))?$/;

function increment(integerPart: string): string {
  let carry = 1;
  let result = "";
  for (let index = integerPart.length - 1; index >= 0; index -= 1) {
    const digit = integerPart.charCodeAt(index) - 48 + carry;
    result = `${digit % 10}${result}`;
    carry = digit >= 10 ? 1 : 0;
  }
  return carry === 1 ? `1${result}` : result;
}

export function formatDecimal(value: string): string {
  const match = decimalPattern.exec(value);
  if (match === null) return value;

  const [, sign, integerPart, fractionPart = ""] = match;
  const hundredths = fractionPart.padEnd(2, "0").slice(0, 2);
  const shouldRoundUp = (fractionPart.charCodeAt(2) || 48) >= 53;
  const roundedHundredths = Number(hundredths) + (shouldRoundUp ? 1 : 0);
  const roundedInteger = roundedHundredths === 100 ? increment(integerPart) : integerPart;
  const roundedFraction = String(roundedHundredths % 100).padStart(2, "0");
  const isZero = /^0+$/.test(roundedInteger) && roundedFraction === "00";

  return `${isZero ? "" : sign}${roundedInteger}.${roundedFraction}`;
}
