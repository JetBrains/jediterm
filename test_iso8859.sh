#!/bin/bash

# Test script for ISO-8859-1 character set support
# Tests the TODO fix at JediTerminal.kt:237

echo "========================================="
echo "ISO-8859-1 Character Set Test"
echo "========================================="
echo ""

# Set ANSI conformance level 2 (uses ISO_LATIN_1 for G1)
printf '\033 M'
echo "✓ Set ANSI conformance level 2 (ISO_LATIN_1 designated to G1)"
echo ""

echo "Testing Latin-1 supplemental characters (160-255):"
echo "-------------------------------------------"

# Map G1 to GR (so GR range 160-255 will use G1 = ISO_LATIN_1)
printf '\033)A'  # Designate ISO Latin-1 to G1 (using 'A' alternate)
printf '\033~'   # Map G1 to GR

echo "Currency symbols:"
printf "  Cent sign: ¢ (U+00A2)\n"
printf "  Pound sign: £ (U+00A3)\n"
printf "  Currency: ¤ (U+00A4)\n"
printf "  Yen: ¥ (U+00A5)\n"
echo ""

echo "Accented characters:"
printf "  À Á Â Ã Ä Å Æ Ç\n"
printf "  È É Ê Ë Ì Í Î Ï\n"
printf "  Ð Ñ Ò Ó Ô Õ Ö\n"
printf "  Ø Ù Ú Û Ü Ý Þ ß\n"
printf "  à á â ã ä å æ ç\n"
printf "  è é ê ë ì í î ï\n"
printf "  ð ñ ò ó ô õ ö\n"
printf "  ø ù ú û ü ý þ ÿ\n"
echo ""

echo "Special symbols:"
printf "  Broken bar: ¦\n"
printf "  Section: §\n"
printf "  Copyright: ©\n"
printf "  Registered: ®\n"
printf "  Degree: °\n"
printf "  Plus-minus: ±\n"
printf "  Micro: µ\n"
printf "  Paragraph: ¶\n"
printf "  Middle dot: ·\n"
printf "  Division: ÷\n"
echo ""

echo "Fractions:"
printf "  Quarter: ¼\n"
printf "  Half: ½\n"
printf "  Three quarters: ¾\n"
echo ""

echo "========================================="
echo "Test complete!"
echo ""
echo "NOTE: To enable ISO-8859-1 mode:"
echo "1. Edit ~/.jediterm/settings.json"
echo "2. Change: \"characterEncoding\": \"ISO-8859-1\""
echo "3. Restart terminal"
echo ""
echo "In UTF-8 mode (default): GR mapping disabled"
echo "In ISO-8859-1 mode: GR mapping enabled"
echo "========================================="
