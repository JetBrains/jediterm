#!/usr/bin/env python3
"""
Generate BossTerm app icon: BO/SS in green on black background.
Creates .iconset folder and converts to .icns using iconutil.
"""

import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

# Icon sizes required for macOS .icns
SIZES = [16, 32, 64, 128, 256, 512, 1024]

# Colors
BLACK = (0, 0, 0)
GREEN = (0, 255, 0)  # Terminal green

def create_icon(size):
    """Create a single icon at the specified size."""
    img = Image.new('RGB', (size, size), BLACK)
    draw = ImageDraw.Draw(img)

    # Calculate font size - each letter fills a quadrant
    font_size = int(size * 0.35)

    # Use Roboto Regular - normal weight
    try:
        font = ImageFont.truetype("/Library/Fonts/Roboto-Regular.ttf", font_size)
    except:
        font = ImageFont.load_default()

    # Letters: B O S S in corners
    # B = top-left, O = top-right, S = bottom-left, S = bottom-right (with cursor)
    letters = [
        ("B", 0, 0),      # top-left quadrant
        ("O", 1, 0),      # top-right quadrant
        ("S", 0, 1),      # bottom-left quadrant
    ]

    half = size // 2

    # Offset to move letters closer to center
    inset = int(size * 0.04)

    for letter, qx, qy in letters:
        # Calculate center point of this quadrant
        center_x = qx * half + half // 2
        center_y = qy * half + half // 2

        # Shift towards center
        if qx == 0:
            center_x += inset  # Move right
        else:
            center_x -= inset  # Move left
        if qy == 0:
            center_y += inset  # Move down
        else:
            center_y -= inset  # Move up

        # Draw letter centered at the quadrant center using anchor
        draw.text((center_x, center_y), letter, fill=GREEN, font=font, anchor="mm")

    # Bottom-right: white rectangular cursor with black "S" on top
    last_s_center_x = 1 * half + half // 2 - inset
    last_s_center_y = 1 * half + half // 2 - inset

    # Get bounding box for the "S" to size the cursor
    bbox = draw.textbbox((last_s_center_x, last_s_center_y), "S", font=font, anchor="mm")

    # Draw white rectangular cursor (bigger than the letter)
    padding_x = int(size * 0.05)
    padding_y = int(size * 0.05)
    WHITE = (180, 180, 180)  # Dimmer white/gray
    cursor_width = int(size * 0.20)  # Wider cursor
    cursor_height = bbox[3] - bbox[1] + padding_y * 2
    cursor_x = last_s_center_x - cursor_width // 2
    cursor_y = bbox[1] - padding_y
    draw.rectangle([cursor_x, cursor_y, cursor_x + cursor_width, cursor_y + cursor_height], fill=WHITE)

    # Draw black "S" on top
    draw.text((last_s_center_x, last_s_center_y), "S", fill=BLACK, font=font, anchor="mm")

    return img

def main():
    # Create iconset directory
    iconset_dir = "BossTerm.iconset"
    os.makedirs(iconset_dir, exist_ok=True)

    # Generate icons at all required sizes
    for size in SIZES:
        img = create_icon(size)

        # Standard resolution
        filename = f"icon_{size}x{size}.png"
        img.save(os.path.join(iconset_dir, filename))
        print(f"Created {filename}")

        # @2x resolution (Retina) - half the dimension name
        if size >= 32:
            half_size = size // 2
            filename_2x = f"icon_{half_size}x{half_size}@2x.png"
            img.save(os.path.join(iconset_dir, filename_2x))
            print(f"Created {filename_2x}")

    # Convert to .icns using iconutil
    print("\nConverting to .icns...")
    result = subprocess.run(
        ["iconutil", "-c", "icns", iconset_dir],
        capture_output=True,
        text=True
    )

    if result.returncode == 0:
        print("Successfully created BossTerm.icns")
        # Clean up iconset directory
        import shutil
        shutil.rmtree(iconset_dir)
        print(f"Cleaned up {iconset_dir}")
    else:
        print(f"Error creating .icns: {result.stderr}")

if __name__ == "__main__":
    main()
