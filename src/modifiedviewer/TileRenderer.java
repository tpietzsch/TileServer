package modifiedviewer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.display.ARGBScreenImage;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.sampler.special.ConstantRandomAccessible;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;
import viewer.GuiHelpers;
import viewer.display.AccumulateARGB;
import viewer.render.Interpolation;
import viewer.render.InterruptibleRenderer;
import viewer.render.Source;
import viewer.render.SourceState;
import viewer.render.ViewerState;

public class TileRenderer
{
	/**
	 * Currently active projector, used to re-paint the display. It maps the
	 * {@link #source} data to {@link #screenImage}.
	 */
	protected InterruptibleRenderer< ?, ARGBType > projector;

	/**
	 * Used to render the tile for encoding.
	 */
	protected ARGBScreenImage screenImage;

	/**
	 * {@link BufferedImage} wrapping the data in the {@link #screenImage}.
	 */
	protected BufferedImage bufferedImage;

	/**
	 * The index of the coarsest mipmap level.
	 */
	protected int[] maxMipmapLevel;

	/**
	 * How many threads to use for rendering.
	 */
	final protected int numRenderingThreads;

	/**
	 * @param numRenderingThreads
	 *            How many threads to use for rendering.
	 */
	public TileRenderer( final int numRenderingThreads )
	{
		projector = null;
		screenImage = null;
		bufferedImage = null;
		maxMipmapLevel = new int[ 0 ];

		this.numRenderingThreads = numRenderingThreads;
	}

	public TileRenderer()
	{
		this( 3 );
	}

	public synchronized ARGBScreenImage getScreenImage()
	{
		return screenImage;
	}

	/**
	 * Check whether the size of the display component was changed and recreate
	 * {@link #screenImages} and {@link #screenScaleTransforms} accordingly.
	 */
	protected synchronized void checkResize( final int tileW, final int tileH )
	{
		if ( screenImage == null || screenImage.dimension( 0 ) != tileW || screenImage.dimension( 1 ) != tileH )
		{
			screenImage = new ARGBScreenImage( tileW, tileH );
			bufferedImage = GuiHelpers.getBufferedImage( screenImage );
		}
	}

	/**
	 * Check whether the number of sources in the state has changed and recreate
	 * mipmap arrays if necessary.
	 */
	protected synchronized void checkNumSourcesChanged( final ViewerState state )
	{
		final int numSources = state.numSources();
		if ( numSources != maxMipmapLevel.length )
		{
			final List< SourceState< ? >> sources = state.getSources();
			maxMipmapLevel = new int[ numSources ];
			for ( int i = 0; i < maxMipmapLevel.length; ++i )
				maxMipmapLevel[ i ] = sources.get( i ).getSpimSource().getNumMipmapLevels() - 1;
		}
	}

	private final static AffineTransform3D identityTransform = new AffineTransform3D();

	/**
	 * Render image at the {@link #requestedScreenScaleIndex requested screen
	 * scale} and the {@link #requestedMipmapLevel requested mipmap level}.
	 */
	public boolean paint( final ViewerState state, final int tileW, final int tileH )
	{
		checkResize( tileW, tileH );
		checkNumSourcesChanged( state );

		final int numSources = state.numSources();

		// the mipmap level that would best suit the current screen scale
		final int[] targetMipmapLevel = new int[ numSources ];

		// the mipmap level at which we will be rendering
		final int[] currentMipmapLevel = targetMipmapLevel;

		// the projector that paints to the screenImage.
		final InterruptibleRenderer< ?, ARGBType > p;

		synchronized ( this )
		{
			for ( int i = 0; i < numSources; ++i )
				targetMipmapLevel[ i ] = state.getBestMipMapLevel( identityTransform, i );

			p = createProjector( state, identityTransform, currentMipmapLevel );
			projector = p;
		}

		// try rendering
		final boolean success = p.map( screenImage, numRenderingThreads );
		final long rendertime = p.getLastFrameRenderNanoTime();
		final long iotime = p.getLastFrameIoNanoTime();

		// if rendering was not cancelled...
		if ( success )
		{
			System.out.println( String.format( "rendering:%4d ms   io:%4d ms   (total:%4d ms)", rendertime / 1000000, iotime / 1000000, ( rendertime + iotime ) / 1000000 ) );
			System.out.println( "mipmap = " + Util.printCoordinates( currentMipmapLevel ) );
		}
		else
			System.out.println( "rendering cancelled" );

		return success;
	}

	/**
	 *
	 * @param screenImage
	 *            render target.
	 * @param screenScaleTransform
	 *            screen scale, transforms screen coordinates to viewer
	 *            coordinates.
	 * @param mipmapIndex
	 *            mipmap level.
	 */
	public static InterruptibleRenderer< ?, ARGBType > createProjector( final ViewerState viewerState, final AffineTransform3D screenScaleTransform, final int[] mipmapIndex )
	{
		synchronized ( viewerState )
		{
			final List< SourceState< ? > > sources = viewerState.getSources();
			final ArrayList< Integer > visibleSourceIndices = viewerState.getVisibleSourceIndices();
			if ( visibleSourceIndices.isEmpty() )
				return new InterruptibleRenderer< ARGBType, ARGBType >( new ConstantRandomAccessible< ARGBType >( argbtype, 2 ), new TypeIdentity< ARGBType >() );
			else if ( visibleSourceIndices.size() == 1 )
			{
				final int i = visibleSourceIndices.get( 0 );
				return createSingleSourceProjector( viewerState, sources.get( i ), screenScaleTransform, mipmapIndex[ i ] );
			}
			else
			{
				final ArrayList< RandomAccessible< ARGBType > > accessibles = new ArrayList< RandomAccessible< ARGBType > >( visibleSourceIndices.size() );
				for ( final int i : visibleSourceIndices )
					accessibles.add( getConvertedTransformedSource( viewerState, sources.get( i ), screenScaleTransform, mipmapIndex[ i ] ) );
				return new InterruptibleRenderer< ARGBType, ARGBType >( new AccumulateARGB( accessibles ), new TypeIdentity< ARGBType >() );
			}
		}
	}

	private final static ARGBType argbtype = new ARGBType();

	private static < T extends NumericType< T > > RandomAccessible< T > getTransformedSource( final ViewerState viewerState, final Source< T > source, final AffineTransform3D screenScaleTransform, final int mipmapIndex )
	{
		final int timepoint = viewerState.getCurrentTimepoint();
		final Interpolation interpolation = viewerState.getInterpolation();
		final RealRandomAccessible< T > img = source.getInterpolatedSource( timepoint, mipmapIndex, interpolation );

		final AffineTransform3D sourceToScreen = new AffineTransform3D();
		viewerState.getViewerTransform( sourceToScreen );
		sourceToScreen.concatenate( source.getSourceTransform( timepoint, mipmapIndex ) );
		sourceToScreen.preConcatenate( screenScaleTransform );

		return RealViews.constantAffine( img, sourceToScreen );
	}

	private static < T extends NumericType< T > > RandomAccessible< ARGBType > getConvertedTransformedSource( final ViewerState viewerState, final SourceState< T > source, final AffineTransform3D screenScaleTransform, final int mipmapIndex )
	{
		return Converters.convert( getTransformedSource( viewerState, source.getSpimSource(), screenScaleTransform, mipmapIndex ), source.getConverter(), argbtype );
	}

	private static < T extends NumericType< T > > InterruptibleRenderer< T, ARGBType > createSingleSourceProjector( final ViewerState viewerState, final SourceState< T > source, final AffineTransform3D screenScaleTransform, final int mipmapIndex )
	{
		return new InterruptibleRenderer< T, ARGBType >( getTransformedSource( viewerState, source.getSpimSource(), screenScaleTransform, mipmapIndex ), source.getConverter() );
	}
}
