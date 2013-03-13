import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.display.ARGBScreenImage;
import net.imglib2.display.RealARGBConverter;
import net.imglib2.display.XYProjector;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import viewer.GuiHelpers;

public class EncodeJpeg
{
	public static byte[] encodeJpeg( final RandomAccessibleInterval< UnsignedByteType > img ) throws IOException
	{
		final ARGBScreenImage argb = new ARGBScreenImage( ( int ) img.dimension( 0 ), ( int ) img.dimension( 1 ) );
		new XYProjector<>( img, argb, new RealARGBConverter< UnsignedByteType >( 0, 255 ) ).map();
		final BufferedImage bi = GuiHelpers.getBufferedImage( argb );

		final ImageWriter jpegWriter = ImageIO.getImageWritersByFormatName( "jpeg" ).next();
		final ImageWriteParam param = jpegWriter.getDefaultWriteParam();
		param.setCompressionMode( ImageWriteParam.MODE_EXPLICIT );
		param.setCompressionQuality( 1f );
		param.setSourceSubsampling( 1, 1, 0, 0 );

		final ByteArrayOutputStream stream = new ByteArrayOutputStream( ( int ) argb.size() );
		final ImageOutputStream ios = ImageIO.createImageOutputStream( stream );
		jpegWriter.setOutput( ios );
		final IIOImage iioImage = new IIOImage( bi, null, null );
		jpegWriter.write( null, iioImage, param );
		ios.close();
		final byte[] data = stream.toByteArray();

		jpegWriter.dispose();

		return data;
	}


	public static void main( final String[] args ) throws ImgIOException, IOException
	{
		final String fn = "/Users/tobias/workspace/catmaid-data/project1/e012t100/20/0_1_0.jpg";
		final UnsignedByteType type = new UnsignedByteType();
		final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
		final ImgOpener opener = new ImgOpener();
		final Img< UnsignedByteType > img = opener.openImg( fn, factory, type );

		encodeJpeg( img );
	}
}
