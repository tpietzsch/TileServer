package tileserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.xml.parsers.ParserConfigurationException;

import net.imglib2.io.ImgIOException;

import org.xml.sax.SAXException;

import viewer.SequenceViewsLoader;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings( "restriction" )
public class TileServerSun
{

	public static void main( final String[] args ) throws Exception
	{
		final HttpServer server = HttpServer.create( new InetSocketAddress( 8010 ), 0 );
		server.createContext( "/", new ImgHandler( 8 ) );
//		final ThreadPoolExecutor executor = new ThreadPoolExecutor(50, 200, 60,
//				TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
//		server.setExecutor(executor);
		server.setExecutor( null ); // creates a default executor
		server.start();
	}

	public static Map< String, String > getParameterMap( final String query )
	{
		final Map< String, String > params = new HashMap< String, String >();
		for ( final String param : query.split( "&" ) )
		{
			final int splitPos = param.indexOf( "=" );
			final String key = param.substring( 0, splitPos );
			final String value = param.substring( splitPos + 1 );
			params.put( key, value );
		}
		return params;
	}

	static class TileGeneratorThread extends Thread
	{
		private final TileGenerator tileGenerator;

		private final BlockingDeque< HttpExchange > requestStack;

		public TileGeneratorThread( final TileGenerator tileGenerator, final BlockingDeque< HttpExchange > requestStack )
		{
			this.tileGenerator = tileGenerator;
			this.requestStack = requestStack;
		}

		@Override
		public void run()
		{
			while ( true )
			{
				try
				{
					final HttpExchange t = requestStack.takeFirst();
					final URI uri = t.getRequestURI();
					final Map< String, String > params = getParameterMap( uri.getQuery() );

					final double x = Double.parseDouble( params.get( "x" ) );
					final double y = Double.parseDouble( params.get( "y" ) );
					final double z = Double.parseDouble( params.get( "z" ) );
					final int timepoint = 0;
					final int tileW = Integer.parseInt( params.get( "width" ) );
					final int tileH = Integer.parseInt( params.get( "height" ) );

					final Headers responseHeaders = t.getResponseHeaders();
					responseHeaders.add( "Content-Type", "image/jpeg" );
					responseHeaders.add( "Cache-Control", "max-age=300" );
					t.sendResponseHeaders( 200, 0 );
					final OutputStream os = t.getResponseBody();
					tileGenerator.getTile( x, y, z, timepoint, tileW, tileH, os );
					os.close();
					t.close();
				}
				catch ( final InterruptedException e )
				{
					break;
				}
				catch ( final IOException e )
				{
					e.printStackTrace();
				}
			}
		}
	}

	static class ImgHandler implements HttpHandler
	{
		private final BlockingDeque< HttpExchange > requestStack;

		public ImgHandler( final int numGeneratorThreads ) throws ImgIOException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, ParserConfigurationException, SAXException
		{
			final SequenceViewsLoader loader = new SequenceViewsLoader( "/Users/tobias/Desktop/e012/test5.xml" );
			requestStack = new LinkedBlockingDeque< HttpExchange >();

			for ( int i = 0; i < numGeneratorThreads; ++i )
				new TileGeneratorThread( new TileGenerator( loader ), requestStack ).start();
		}

		int ignoreCount = 5;

		@Override
		public void handle( final HttpExchange t ) throws IOException
		{
			try
			{
				if ( ignoreCount > 0 )
				{
					--ignoreCount;
					System.out.println( "ignoring request" );
				}
				else
				{
					requestStack.putFirst( t );
					System.out.println( "added request" );
				}
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}
		}
	}
}
