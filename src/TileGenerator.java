import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import modifiedviewer.TileRenderer;
import mpicbg.spim.data.SequenceDescription;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.RealARGBConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.xml.sax.SAXException;

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

	public TileGenerator( final String xmlFilename ) throws ParserConfigurationException, SAXException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final SequenceViewsLoader loader = new SequenceViewsLoader( xmlFilename );
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

		renderer = new TileRenderer();
	}

	public void getTile( final double x, final double y, final double z, final int t, final int tileW, final int tileH )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				1, 0, 0, -x,
				0, 1, 0, -y,
				0, 0, 1, -z );
		state.setViewerTransform( transform );
		renderer.paint( state, tileW, tileH );
		ImageJFunctions.show( renderer.getScreenImage() );
	}
}
