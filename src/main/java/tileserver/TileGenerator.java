package tileserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import modifiedviewer.TileRenderer;
import mpicbg.spim.data.SequenceDescription;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.RealARGBConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import viewer.SequenceViewsLoader;
import viewer.SpimSource;
import viewer.render.Interpolation;
import viewer.render.SourceAndConverter;
import viewer.render.ViewerState;


public class TileGenerator
{
	final ArrayList< AbstractLinearRange > displayRanges;

	final ViewerState state;

	final TileRenderer renderer;

	final ImageWriter jpegWriter;

	final ImageWriteParam param;

	public TileGenerator( final SequenceViewsLoader loader )
	{
		final SequenceDescription seq = loader.getSequenceDescription();

		displayRanges = new ArrayList< AbstractLinearRange >();
		final RealARGBConverter< UnsignedShortType > converter = new RealARGBConverter< UnsignedShortType >( 0, 6000 /*65535*/ );
		displayRanges.add( converter );

		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();
		for ( int setup = 0; setup < seq.numViewSetups(); ++setup )
			sources.add( new SourceAndConverter< UnsignedShortType >( new SpimSource( loader, setup, "angle " + seq.setups[ setup ].getAngle() ), converter ) );

		state = new ViewerState( sources, seq.numTimepoints() );
		state.setCurrentSource( 0 );
		state.setInterpolation( Interpolation.NLINEAR );

		renderer = new TileRenderer( 4 );

		jpegWriter = ImageIO.getImageWritersByFormatName( "jpeg" ).next();
		param = jpegWriter.getDefaultWriteParam();
		param.setCompressionMode( ImageWriteParam.MODE_EXPLICIT );
		param.setCompressionQuality( 1f );
		param.setSourceSubsampling( 1, 1, 0, 0 );
	}

	public void getTile( final AffineTransform3D viewTransform, final int t, final int tileW, final int tileH, final double screenScale, final Interpolation interpolation, final OutputStream os ) throws IOException
	{
		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		screenScaleTransform.set( screenScale, 0, 0 );
		screenScaleTransform.set( screenScale, 1, 1 );
		screenScaleTransform.set( 0.5 * screenScale - 0.5, 0, 3 );
		screenScaleTransform.set( 0.5 * screenScale - 0.5, 1, 3 );
		viewTransform.preConcatenate( screenScaleTransform );

		state.setViewerTransform( viewTransform );
		state.setCurrentTimepoint( t );
		state.setInterpolation( interpolation );
		renderer.paint( state, ( int ) ( screenScale * tileW ), ( int ) ( screenScale * tileH ) );

		final ImageOutputStream ios = ImageIO.createImageOutputStream( os );
		jpegWriter.setOutput( ios );
		jpegWriter.write( null, new IIOImage( renderer.getBufferedImage(), null, null ), param );
		ios.close();
	}

	public void getTile( final double x, final double y, final double z, final double scale, final int t, final int tileW, final int tileH, final OutputStream os ) throws IOException
	{
		final double screenScale = 0.5;
		final AffineTransform3D transform = new AffineTransform3D();
		final double s = 1;
		transform.set(
				s * scale, 0, 0, -x,
				0, s * scale, 0, -y,
				0, 0, s * scale, -z * scale );

		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		screenScaleTransform.set( screenScale, 0, 0 );
		screenScaleTransform.set( screenScale, 1, 1 );
		screenScaleTransform.set( 0.5 * screenScale - 0.5, 0, 3 );
		screenScaleTransform.set( 0.5 * screenScale - 0.5, 1, 3 );
		transform.preConcatenate( screenScaleTransform );

		state.setViewerTransform( transform );
		state.setCurrentTimepoint( t );
		renderer.paint( state, ( int ) ( screenScale * tileW ), ( int ) ( screenScale * tileH ) );

		final ImageOutputStream ios = ImageIO.createImageOutputStream( os );
		jpegWriter.setOutput( ios );
		jpegWriter.write( null, new IIOImage( renderer.getBufferedImage(), null, null ), param );
		ios.close();
	}
}
