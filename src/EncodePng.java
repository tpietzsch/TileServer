import java.io.IOException;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.integer.UnsignedByteType;


public class EncodePng
{
	public static byte[] encodePng( final ArrayImg< UnsignedByteType, ByteArray > img ) throws IOException
	{
		final byte[] gray = img.update( null ).getCurrentStorageArray();
		final byte[] data = PNG.toPNG( ( int ) img.dimension( 0 ), ( int ) img.dimension( 1 ), null, gray, gray, gray );
		return data;
	}

	public static void main( final String[] args ) throws ImgIOException, IOException
	{
		final String fn = "/Users/tobias/workspace/catmaid-data/project1/e012t100/20/0_1_0.jpg";
		final UnsignedByteType type = new UnsignedByteType();
		final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
		final ImgOpener opener = new ImgOpener();
		final ArrayImg< UnsignedByteType, ByteArray > img = ( ArrayImg< UnsignedByteType, ByteArray > ) opener.openImg( fn, factory, type ).getImg();

		encodePng( img );
	}

}
