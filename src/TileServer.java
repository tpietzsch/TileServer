import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings( "restriction" )
public class TileServer
{

	public static void main( final String[] args ) throws Exception
	{
		final HttpServer server = HttpServer.create( new InetSocketAddress( 8000 ), 0 );
		server.createContext( "/test", new MyHandler() );
		server.createContext( "/img", new ImgHandler( true ) );
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
		private final byte [] imgData;

		public ImgHandler( final boolean encode ) throws ImgIOException, IOException
		{
			final String fn = "/Users/tobias/workspace/catmaid-data/project1/e012t100/20/0_1_0.jpg";
			final UnsignedByteType type = new UnsignedByteType();
			final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory< UnsignedByteType >();
			final ImgOpener opener = new ImgOpener();
			final Img< UnsignedByteType > img = opener.openImg( fn, factory, type );

			imgData = EncodeJpeg.encodeJpeg( img );
		}

		@Override
		public void handle( final HttpExchange t ) throws IOException
		{
			final Headers responseHeaders = t.getResponseHeaders();
			responseHeaders.add( "Content-Type", "image/jpeg" );
			t.sendResponseHeaders( 200, imgData.length );
			final OutputStream os = t.getResponseBody();
			os.write( imgData );
			os.close();
		}
	}

}
