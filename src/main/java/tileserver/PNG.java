package tileserver;

/*
 * Minimal PNG encoder to create PNG streams (and MIDP images) from RGBA arrays.
 *
 * Copyright 2006-2009 Christian Froeschlin
 *
 * www.chrfr.de
 *
 *
 * Changelog:
 *
 * 09/22/08: Fixed Adler checksum calculation and byte order
 *           for storing length of zlib deflate block. Thanks
 *           to Miloslav Ruzicka for noting this.
 *
 * 05/12/09: Split PNG and ZLIB functionality into separate classes.
 *           Added support for images > 64K by splitting the data into
 *           multiple uncompressed deflate blocks.
 *
 * Terms of Use:
 *
 * You may use the PNG encoder free of charge for any purpose you desire, as long
 * as you do not claim credit for the original sources and agree not to hold me
 * responsible for any damage arising out of its use.
 *
 * If you have a suitable location in GUI or documentation for giving credit,
 * I'd appreciate a mention of
 *
 *  PNG encoder (C) 2006-2009 by Christian Froeschlin, www.chrfr.de
 *
 * but that's not mandatory.
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PNG
{
  public static byte[] toPNG(final int width, final int height, final byte[] alpha, final byte[] red, final byte[] green, final byte[] blue) throws IOException
  {
    final byte[] signature = new byte[] {(byte) 137, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10, (byte) 26, (byte) 10};
    final byte[] header = createHeaderChunk(width, height);
    final byte[] data = createDataChunk(width, height, alpha, red, green, blue);
    final byte[] trailer = createTrailerChunk();

    final ByteArrayOutputStream png = new ByteArrayOutputStream(signature.length + header.length + data.length + trailer.length);
    png.write(signature);
    png.write(header);
    png.write(data);
    png.write(trailer);
    return png.toByteArray();
  }

  public static byte[] createHeaderChunk(final int width, final int height) throws IOException
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(13);
    final DataOutputStream chunk = new DataOutputStream(baos);
    chunk.writeInt(width);
    chunk.writeInt(height);
    chunk.writeByte(8); // Bitdepth
    chunk.writeByte(6); // Colortype ARGB
    chunk.writeByte(0); // Compression
    chunk.writeByte(0); // Filter
    chunk.writeByte(0); // Interlace
    return toChunk("IHDR", baos.toByteArray());
  }

  public static byte[] createDataChunk(final int width, final int height, final byte[] alpha, final byte[] red, final byte[] green, final byte[] blue) throws IOException
  {
    int source = 0;
    int dest = 0;
    final byte[] raw = new byte[4*(width*height) + height];
    for (int y = 0; y < height; y++)
    {
      raw[dest++] = 0; // No filter
      for (int x = 0; x < width; x++)
      {
        raw[dest++] = red[source];
        raw[dest++] = green[source];
        raw[dest++] = blue[source++];
        raw[dest++] = -1;
      }
    }
    return toChunk("IDAT", toZLIB(raw));
  }

  public static byte[] createTrailerChunk() throws IOException
  {
    return toChunk("IEND", new byte[] {});
  }

  public static byte[] toChunk(final String id, final byte[] raw) throws IOException
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length + 12);
    final DataOutputStream chunk = new DataOutputStream(baos);

    chunk.writeInt(raw.length);

    final byte[] bid = new byte[4];
    for (int i = 0; i < 4; i++)
    {
      bid[i] = (byte) id.charAt(i);
    }

    chunk.write(bid);

    chunk.write(raw);

    int crc = 0xFFFFFFFF;
    crc = updateCRC(crc, bid);
    crc = updateCRC(crc, raw);
    chunk.writeInt(~crc);

    return baos.toByteArray();
  }

  static int[] crcTable = null;

  public static void createCRCTable()
  {
    crcTable = new int[256];

    for (int i = 0; i < 256; i++)
    {
      int c = i;
      for (int k = 0; k < 8; k++)
      {
        c = ((c & 1) > 0) ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      }
      crcTable[i] = c;
    }
  }

  public static int updateCRC(int crc, final byte[] raw)
  {
    if (crcTable == null)
    {
      createCRCTable();
    }

    for (int i = 0; i < raw.length; i++)
    {
      crc = crcTable[(crc ^ raw[i]) & 0xFF] ^ (crc >>> 8);
    }

    return crc;
  }

  /* This method is called to encode the image data as a zlib
     block as required by the PNG specification. This file comes
     with a minimal ZLIB encoder which uses uncompressed deflate
     blocks (fast, short, easy, but no compression). If you want
     compression, call another encoder (such as JZLib?) here. */
  public static byte[] toZLIB(final byte[] raw) throws IOException
  {
    return ZLIB.toZLIB(raw);
  }
}



class ZLIB
{
  static final int BLOCK_SIZE = 32000;

  public static byte[] toZLIB(final byte[] raw) throws IOException
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length + 6 + (raw.length / BLOCK_SIZE) * 5);
    final DataOutputStream zlib = new DataOutputStream(baos);

    final byte tmp = (byte) 8;
    zlib.writeByte(tmp);                           // CM = 8, CMINFO = 0
    zlib.writeByte((31 - ((tmp << 8) % 31)) % 31); // FCHECK (FDICT/FLEVEL=0)

    int pos = 0;
    while (raw.length - pos > BLOCK_SIZE)
    {
      writeUncompressedDeflateBlock(zlib, false, raw, pos, (char) BLOCK_SIZE);
      pos += BLOCK_SIZE;
    }

    writeUncompressedDeflateBlock(zlib, true, raw, pos, (char) (raw.length - pos));

    // zlib check sum of uncompressed data
    zlib.writeInt(calcADLER32(raw));

    return baos.toByteArray();
  }

  private static void writeUncompressedDeflateBlock(final DataOutputStream zlib, final boolean last,
                        final byte[] raw, final int off, final char len) throws IOException
  {
    zlib.writeByte((byte)(last ? 1 : 0));         // Final flag, Compression type 0
    zlib.writeByte((byte)(len & 0xFF));           // Length LSB
    zlib.writeByte((byte)((len & 0xFF00) >> 8));  // Length MSB
    zlib.writeByte((byte)(~len & 0xFF));          // Length 1st complement LSB
    zlib.writeByte((byte)((~len & 0xFF00) >> 8)); // Length 1st complement MSB
    zlib.write(raw,off,len);                      // Data
  }

  private static int calcADLER32(final byte[] raw)
  {
    int s1 = 1;
    int s2 = 0;
    for (int i = 0; i < raw.length; i++)
    {
      final int abs = raw[i] >=0 ? raw[i] : (raw[i] + 256);
      s1 = (s1 + abs) % 65521;
      s2 = (s2 + s1) % 65521;
    }
    return (s2 << 16) + s1;
  }
}
