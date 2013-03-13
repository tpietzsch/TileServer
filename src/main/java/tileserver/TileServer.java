package tileserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import net.imglib2.io.ImgIOException;

import org.xml.sax.SAXException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings( "restriction" )
public class TileServer
{

	public static void main( final String[] args ) throws Exception
	{
		final HttpServer server = HttpServer.create( new InetSocketAddress( 8010 ), 0 );
		server.createContext( "/", new ImgHandler( true ) );
		server.setExecutor( null ); // creates a default executor
		server.start();
	}

	static class MyHandler implements HttpHandler
	{
		@Override
		public void handle( final HttpExchange t ) throws IOException
		{
			final String response = "This is the response";
			t.sendResponseHeaders( 200, response.length() );
			final OutputStream os = t.getResponseBody();
			os.write( response.getBytes() );
			os.close();
		}
	}

	static class ImgHandler implements HttpHandler
	{
		private final TileGenerator tileGenerator;

		public ImgHandler( final boolean encode ) throws ImgIOException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, ParserConfigurationException, SAXException
		{
			tileGenerator = new TileGenerator( "/Users/tobias/Desktop/e012/test5.xml" );
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

		@Override
		public void handle( final HttpExchange t ) throws IOException
		{
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
		}
	}

}