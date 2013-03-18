package tileserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import net.imglib2.io.ImgIOException;
import net.imglib2.realtransform.AffineTransform3D;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.xml.sax.SAXException;

import viewer.SequenceViewsLoader;
import viewer.render.Interpolation;

public class TileServerJetty
{
    public static void main(final String[] args) throws Exception
    {
        final Server server = new Server( 8010 );
        server.setHandler( new ImgHandler( 16 ) );

        server.start();
        server.join();
    }

	static class ImgHandler extends AbstractHandler
	{
		private final BlockingDeque< TileGenerator > idleGenerators;

		public ImgHandler( final int numGenerators ) throws ImgIOException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, ParserConfigurationException, SAXException
		{
			final SequenceViewsLoader loader = new SequenceViewsLoader( "/Users/tobias/Desktop/e012/test5.xml" );
			idleGenerators = new LinkedBlockingDeque< TileGenerator >();

			for ( int i = 0; i < numGenerators; ++i )
				idleGenerators.addFirst( new TileGenerator( loader ) );
		}

		private static double tryGetDouble( final HttpServletRequest request, final String name )
		{
			final String param = request.getParameter( name );
			if ( param == null )
				return 0;
			try
			{
				return Double.parseDouble( param );
			}
			catch ( final NumberFormatException e )
			{
				return 0;
			}
		}

		private static int tryGetInt( final HttpServletRequest request, final String name )
		{
			final String param = request.getParameter( name );
			if ( param == null )
				return 0;
			try
			{
				return Integer.parseInt( param );
			}
			catch ( final NumberFormatException e )
			{
				return 0;
			}
		}

		private static String tryGetString( final HttpServletRequest request, final String name )
		{
			final String param = request.getParameter( name );
			if ( param == null )
				return "";
			return param;
		}

	    @Override
		public void handle(final String target,
	                       final Request baseRequest,
	                       final HttpServletRequest request,
	                       final HttpServletResponse response)
	        throws IOException, ServletException
	    {
			try
			{
				final TileGenerator generator = idleGenerators.takeFirst();

//				final double x = tryGetDouble( request, "x" );
//				final double y = tryGetDouble( request, "y" );
//				final double z = tryGetDouble( request, "z" );
//				final double scale = tryGetDouble( request, "scale" );
				final int timepoint = tryGetInt( request, "timepoint" );
				final double screenScale = tryGetDouble( request, "screenscale" );
				final Interpolation interpolation = tryGetString( request, "interpolation" ).equals( "NLINEAR" ) ? Interpolation.NLINEAR : Interpolation.NEARESTNEIGHBOR;
				final int tileW = tryGetInt( request, "width" );
				final int tileH = tryGetInt( request, "height" );

				final double[][] values = new double[ 3 ][ 4 ];
				for ( int r = 0; r < 3; ++r )
					for ( int c = 0; c < 4; ++c )
						values[ r ][ c ] = tryGetDouble( request, "a" + r + "" + c );
				final AffineTransform3D stackToTile = new AffineTransform3D();
				stackToTile.set( values );

				response.setContentType( "image/jpeg" );
				response.setStatus( HttpServletResponse.SC_OK );
				response.addHeader( "Cache-Control", "max-age=20" );

				baseRequest.setHandled(true);

				final OutputStream os = response.getOutputStream();
//				generator.getTile( x, y, z, scale, timepoint, tileW, tileH, os );
				generator.getTile( stackToTile, timepoint, tileW, tileH, screenScale, interpolation, os );
				os.close();

				idleGenerators.putFirst( generator );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}
	    }
	}

}
