package dropbox;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;

public interface DropboxConnector
{
	// Start Auth on link account button

	//auth

	//save auth then build client and start using client

	//delink auth

	//client
	public DbxClientV2 dropboxClient() throws DbxException;

	//user info call

	//file stuff
	//get
	//put
	//post
	//delete

}
