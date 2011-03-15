/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */
/*
 * Copyright (C) 2011 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */
package com.unboundid.ldap.listener;



import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.net.SocketFactory;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.protocol.AddRequestProtocolOp;
import com.unboundid.ldap.protocol.AddResponseProtocolOp;
import com.unboundid.ldap.protocol.CompareRequestProtocolOp;
import com.unboundid.ldap.protocol.CompareResponseProtocolOp;
import com.unboundid.ldap.protocol.DeleteRequestProtocolOp;
import com.unboundid.ldap.protocol.DeleteResponseProtocolOp;
import com.unboundid.ldap.protocol.ExtendedRequestProtocolOp;
import com.unboundid.ldap.protocol.ExtendedResponseProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.ModifyRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyResponseProtocolOp;
import com.unboundid.ldap.protocol.ModifyDNRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyDNResponseProtocolOp;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.protocol.SearchResultDoneProtocolOp;
import com.unboundid.ldap.sdk.AddRequest;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.CompareRequest;
import com.unboundid.ldap.sdk.CompareResult;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.DeleteRequest;
import com.unboundid.ldap.sdk.DereferencePolicy;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.ExtendedRequest;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.InternalSDKHelper;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModifyRequest;
import com.unboundid.ldap.sdk.ModifyDNRequest;
import com.unboundid.ldap.sdk.ReadOnlyAddRequest;
import com.unboundid.ldap.sdk.ReadOnlyCompareRequest;
import com.unboundid.ldap.sdk.ReadOnlyDeleteRequest;
import com.unboundid.ldap.sdk.ReadOnlyModifyRequest;
import com.unboundid.ldap.sdk.ReadOnlyModifyDNRequest;
import com.unboundid.ldap.sdk.ReadOnlySearchRequest;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultListener;
import com.unboundid.ldap.sdk.SearchResultReference;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFWriter;
import com.unboundid.util.ByteStringBuffer;
import com.unboundid.util.Debug;
import com.unboundid.util.Mutable;
import com.unboundid.util.StaticUtils;
import com.unboundid.util.ThreadSafety;
import com.unboundid.util.ThreadSafetyLevel;
import com.unboundid.util.Validator;

import static com.unboundid.ldap.listener.ListenerMessages.*;



/**
 * This class provides a utility that may be used to create a simple LDAP server
 * instance that will hold all of its information in memory.  It is intended to
 * be very easy to use, particularly as an embeddable server for testing
 * directory-enabled applications.  It can be easily created, configured,
 * populated, and shut down with only a few lines of code, and it provides a
 * number of convenience methods that can be very helpful in writing test cases
 * that validate the content of the server.
 * <BR><BR>
 * Some notes about the capabilities of this server:
 * <UL>
 *   <LI>It provides reasonably complete support for add, compare, delete,
 *       modify, modify DN (including new superior and subtree move/rename),
 *       search, and unbind operations.</LI>
 *   <LI>It will accept abandon requests, but will not do anything with
 *       them.</LI>
 *   <LI>It provides support for simple bind operations, and for the SASL PLAIN
 *       mechanism.  It also provides an API that can be used to add support for
 *       additional SASL mechanisms.</LI>
 *   <LI>It provides support for the password modify and "who am I?" extended
 *       operations, as well as an API that can be used to add support for
 *       additional types of extended operations.</LI>
 *   <LI>It provides support for the LDAP assertions, authorization identity,
 *       manage DSA IT, permissive modify, pre-read, post-read, proxied
 *       authorization v1 and v2, server-side sort, simple paged results,
 *       LDAP subentries, subtree delete, and virtual list view request
 *       controls.</LI>
 *   <LI>It supports the use of schema (if provided), but it does not currently
 *       allow updating the schema on the fly.</LI>
 *   <LI>It has the ability to maintain a log of operations processed, either
 *       as a simple access log or a more detailed LDAP debug log.</LI>
 *   <LI>It has the ability to maintain an LDAP-accessible changelog.</LI>
 *   <LI>It provides an option to generate a number of operational attributes,
 *       including entryDN, entryUUID, creatorsName, createTimestamp,
 *       modifiersName, modifyTimestamp, and subschemaSubentry.</LI>
 *   <LI>It provides methods for importing data from and exporting data to LDIF
 *       files, and it has the ability to capture a point-in-time snapshot of
 *       the data (including changelog information) that may be restored at any
 *       point.</LI>
 *   <LI>It implements the {@link LDAPInterface} interface, which means that in
 *       many cases it can be used as a drop-in replacement for an
 *       {@link LDAPConnection}.</LI>
 * </UL>
 * <BR><BR>
 * In order to create an in-memory directory server instance, you should first
 * create an {@link InMemoryDirectoryServerConfig} object with the desired
 * settings.  Then use that configuration object to initialize the directory
 * server instance, and call the {@link #startListening} method to start
 * accepting connections from LDAP clients.  The {@link #getConnection} and
 * {@link #getConnectionPool} methods may be used to obtain connections to the
 * server, and you can also manually create connections using the information
 * obtained via the {@link #getListenAddress}, {@link #getListenPort}, and
 * {@link #getClientSocketFactory} methods.  When the server is no longer
 * needed, the {@link #shutDown} method should be used to stop the server.  Any
 * number of in-memory directory server instances can be created and running in
 * a single JVM at any time, and many of the methods provided in this class can
 * be used without the server running if operations are to be performed using
 * only method calls rather than via LDAP clients.
 * <BR><BR>
 * <H2>Example</H2>
 * The following example demonstrates the process that can be used to create,
 * start, and use an in-memory directory server instance:
 * <PRE>
 * // Create a base configuration for the server.
 * InMemoryDirectoryServerConfig config =
 *      new InMemoryDirectoryServerConfig("dc=example,dc=com");
 * config.setSchema(Schema.getDefaultStandardSchema());
 * config.addAdditionalBindCredentials("cn=Directory Manager",
 *      "password");
 *
 * // Create and start the server instance and populate it with an
 * // initial set of data from the file "/tmp;test.ldif".
 * InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
 * server.initializeFromLDIF(true, "/tmp/test.ldif");
 *
 * // Start the server so it will accept client connections.
 * int listenPort = server.startListening();
 *
 * // Get a connection to the server.
 * LDAPConnection conn = server.getConnection();
 *
 * // Perform various operations in the server....
 *
 * // Close the connection.
 * conn.close();
 *
 * // Shut down the server so that it will no longer accept client
 * // connections, and close all existing connections.
 * server.shutDown(true);
 * </PRE>
 */
@Mutable()
@ThreadSafety(level=ThreadSafetyLevel.COMPLETELY_THREADSAFE)
public final class InMemoryDirectoryServer
       implements LDAPInterface
{
  // The in-memory request handler that will be used for the server.
  private final InMemoryRequestHandler inMemoryHandler;

  // The LDAP listener that will be used to interact with clients.
  private final LDAPListener listener;

  // A read-only representation of the configuration used to create this
  // in-memory directory server.
  private final ReadOnlyInMemoryDirectoryServerConfig config;

  // The socket factory that should be used when trying to create client
  // connections.
  private final SocketFactory clientSocketFactory;



  /**
   * Creates a very simple instance of an in-memory directory server with the
   * specified set of base DNs.  It will not use a well-defined schema, and will
   * pick a listen port at random.
   *
   * @param  baseDNs  The base DNs to use for the server.  It must not be
   *                  {@code null} or empty.
   *
   * @throws  LDAPException  If a problem occurs while attempting to initialize
   *                         the server.
   */
  public InMemoryDirectoryServer(final String... baseDNs)
         throws LDAPException
  {
    this(new InMemoryDirectoryServerConfig(baseDNs));
  }



  /**
   * Creates a new instance of an in-memory directory server with the provided
   * configuration.
   *
   * @param  config  The configuration to use for the server.  It must not be
   *                 {@code null}.
   *
   * @throws  LDAPException  If a problem occurs while trying to initialize the
   *                         directory server with the provided configuration.
   */
  public InMemoryDirectoryServer(final InMemoryDirectoryServerConfig config)
         throws LDAPException
  {
    Validator.ensureNotNull(config);

    this.config = new ReadOnlyInMemoryDirectoryServerConfig(config);

    inMemoryHandler = new InMemoryRequestHandler(config);

    LDAPListenerRequestHandler requestHandler = inMemoryHandler;

    if (config.getAccessLogHandler() != null)
    {
      requestHandler = new AccessLogRequestHandler(config.getAccessLogHandler(),
           requestHandler);
    }

    if (config.getLDAPDebugLogHandler() != null)
    {
      requestHandler = new LDAPDebuggerRequestHandler(
           config.getLDAPDebugLogHandler(), requestHandler);
    }

    final LDAPListenerConfig listenerConfig =
         new LDAPListenerConfig(config.getListenPort(), requestHandler);
    listenerConfig.setExceptionHandler(config.getListenerExceptionHandler());
    listenerConfig.setListenAddress(config.getListenAddress());
    listenerConfig.setServerSocketFactory(config.getServerSocketFactory());

    listener = new LDAPListener(listenerConfig);

    clientSocketFactory = config.getClientSocketFactory();
  }



  /**
   * Causes the server to start listening for client connections.  This method
   * will return as soon as the listener has started.  This method may only be
   * called once on a single object instance, so one a server has been shut
   * down it cannot be re-started, and it will be necessary to create a new
   * instance and start that.
   *
   * @return  The port on which the server is listening for client connections.
   *
   * @throws  IOException  If a problem occurs while attempting to create the
   *                       listen socket.
   */
  public int startListening()
         throws IOException
  {
    listener.startListening();
    return listener.getListenPort();
  }



  /**
   * Indicates that the server should stop accepting new connections.  It may
   * optionally close all connections that have already been established.  Note
   * that once a server instance has been shut down, it cannot be re-started,
   * and a new instance will be required.
   *
   * @param  closeExistingConnections  Indicates whether to close all existing
   *                                   connections, or merely to stop accepting
   *                                   new connections.
   */
  public void shutDown(final boolean closeExistingConnections)
  {
    listener.shutDown(closeExistingConnections);
  }



  /**
   * Retrieves a read-only representation of the configuration used to create
   * this in-memory directory server instance.
   *
   * @return  A read-only representation of the configuration used to create
   *          this in-memory directory server instance.
   */
  public ReadOnlyInMemoryDirectoryServerConfig getConfig()
  {
    return config;
  }



  /**
   * Retrieves the in-memory request handler that is used to perform the real
   * server processing.
   *
   * @return  The in-memory request handler that is used to perform the real
   *          server processing.
   */
  InMemoryRequestHandler getInMemoryRequestHandler()
  {
    return inMemoryHandler;
  }



  /**
   * Creates a point-in-time snapshot of the information contained in this
   * in-memory directory server instance.  It may be restored using the
   * {@link #restoreSnapshot} method.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @return  The snapshot created based on the current content of this
   *          in-memory directory server instance.
   */
  public InMemoryDirectoryServerSnapshot createSnapshot()
  {
    return inMemoryHandler.createSnapshot();
  }



  /**
   * Restores the this in-memory directory server instance to match the content
   * it held at the time the snapshot was created.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  snapshot  The snapshot to be restored.  It must not be
   *                   {@code null}.
   */
  public void restoreSnapshot(final InMemoryDirectoryServerSnapshot snapshot)
  {
    inMemoryHandler.restoreSnapshot(snapshot);
  }



  /**
   * Retrieves the address on which the server is currently listening for client
   * connections.
   *
   * @return  The address on which the server is currently listening for client
   *          connections, or {@code null} if it is not currently listening.
   */
  public InetAddress getListenAddress()
  {
    return listener.getListenAddress();
  }



  /**
   * Retrieves the port on which the server is currently listening for client
   * connections.
   *
   * @return  The port on which the server is currently listening for client
   *          connections, or -1 if it is not currently listening.
   */
  public int getListenPort()
  {
    return listener.getListenPort();
  }



  /**
   * Retrieves the socket factory that should be used when establishing
   * connections to the server, if defined.
   *
   * @return  The socket factory that should be used when establishing
   *          connections to the server, or {@code null} if the JVM-default
   *          socket factory should be used.
   */
  public SocketFactory getClientSocketFactory()
  {
    return clientSocketFactory;
  }



  /**
   * Retrieves the list of base DNs configured for use by the server.
   *
   * @return  The list of base DNs configured for use by the server.
   */
  public List<DN> getBaseDNs()
  {
    return inMemoryHandler.getBaseDNs();
  }



  /**
   * Attempts to establish a client connection to the server.
   *
   * @return  The client connection that has been established.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to
   *                         create the connection.
   */
  public LDAPConnection getConnection()
         throws LDAPException
  {
    return getConnection(null);
  }



  /**
   * Attempts to establish a client connection to the server.
   *
   * @param  options  The connection options to use when creating the
   *                  connection.  It may be {@code null} if a default set of
   *                  options should be used.
   *
   * @return  The client connection that has been established.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to
   *                         create the connection.
   */
  public LDAPConnection getConnection(final LDAPConnectionOptions options)
         throws LDAPException
  {
    final int listenPort = listener.getListenPort();
    if (listenPort < 0)
    {
      throw new LDAPException(ResultCode.CONNECT_ERROR,
           ERR_MEM_DS_GET_CONNECTION_NOT_LISTENING.get());
    }

    String hostAddress;
    final InetAddress listenAddress = listener.getListenAddress();
    if (listenAddress.isAnyLocalAddress())
    {
      try
      {
        hostAddress = InetAddress.getLocalHost().getHostAddress();
      }
      catch (final Exception e)
      {
        Debug.debugException(e);
        hostAddress = "127.0.0.1";
      }
    }
    else
    {
      hostAddress = listenAddress.getHostAddress();
    }

    return new LDAPConnection(clientSocketFactory, options, hostAddress,
         listenPort);
  }



  /**
   * Attempts to establish a connection pool to the server with the specified
   * maximum number of connections.
   *
   * @param  maxConnections  The maximum number of connections to maintain in
   *                         the connection pool.  It must be greater than or
   *                         equal to one.
   *
   * @return  The connection pool that has been created.
   *
   * @throws  LDAPException  If a problem occurs while attempting to create the
   *                         connection pool.
   */
  public LDAPConnectionPool getConnectionPool(final int maxConnections)
         throws LDAPException
  {
    return getConnectionPool(null, 1, maxConnections);
  }



  /**
   * Attempts to establish a connection pool to the server with the provided
   * settings.
   *
   * @param  options             The connection options to use when creating
   *                             connections for use in the pool.  It may be
   *                             {@code null} if a default set of options should
   *                             be used.
   * @param  initialConnections  The initial number of connections to establish
   *                             in the connection pool.  It must be greater
   *                             than or equal to one.
   * @param  maxConnections      The maximum number of connections to maintain
   *                             in the connection pool.  It must be greater
   *                             than or equal to the initial number of
   *                             connections.
   *
   * @return  The connection pool that has been created.
   *
   * @throws  LDAPException  If a problem occurs while attempting to create the
   *                         connection pool.
   */
  public LDAPConnectionPool getConnectionPool(
                                 final LDAPConnectionOptions options,
                                 final int initialConnections,
                                 final int maxConnections)
         throws LDAPException
  {
    final LDAPConnection conn = getConnection(options);
    return new LDAPConnectionPool(conn, initialConnections, maxConnections);
  }



  /**
   * Retrieves the number of entries currently held in the server.  The count
   * returned will not include entries which are part of the changelog.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @return  The number of entries currently held in the server.
   */
  public int countEntries()
  {
    return countEntries(false);
  }



  /**
   * Retrieves the number of entries currently held in the server, optionally
   * including those entries which are part of the changelog.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  includeChangeLog  Indicates whether to include entries that are
   *                           part of the changelog in the count.
   *
   * @return  The number of entries currently held in the server.
   */
  public int countEntries(final boolean includeChangeLog)
  {
    return inMemoryHandler.countEntries(includeChangeLog);
  }



  /**
   * Retrieves the number of entries currently held in the server whose DN
   * matches or is subordinate to the provided base DN.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  baseDN  The base DN to use for the determination.
   *
   * @return  The number of entries currently held in the server whose DN
   *          matches or is subordinate to the provided base DN.
   *
   * @throws  LDAPException  If the provided string cannot be parsed as a valid
   *                         DN.
   */
  public int countEntriesBelow(final String baseDN)
         throws LDAPException
  {
    return inMemoryHandler.countEntriesBelow(baseDN);
  }



  /**
   * Removes all entries currently held in the server.  If a changelog is
   * enabled, then all changelog entries will also be cleared but the base
   * "cn=changelog" entry will be retained.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public void clear()
  {
    inMemoryHandler.clear();
  }



  /**
   * Reads entries from the specified LDIF file and adds them to the server,
   * optionally clearing any existing entries before beginning to add the new
   * entries.  If an error is encountered while adding entries from LDIF then
   * the server will remain populated with the data it held before the import
   * attempt (even if the {@code clear} is given with a value of {@code true}).
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  clear  Indicates whether to remove all existing entries prior to
   *                adding entries read from LDIF.
   * @param  path   The path to the LDIF file from which the entries should be
   *                read.  It must not be {@code null}.
   *
   * @return  The number of entries read from LDIF and added to the server.
   *
   * @throws  LDAPException  If a problem occurs while reading entries or adding
   *                         them to the server.
   */
  public int importFromLDIF(final boolean clear, final String path)
         throws LDAPException
  {
    final LDIFReader reader;
    try
    {
      reader = new LDIFReader(path);
    }
    catch (final Exception e)
    {
      Debug.debugException(e);
      throw new LDAPException(ResultCode.LOCAL_ERROR,
           ERR_MEM_DS_INIT_FROM_LDIF_CANNOT_CREATE_READER.get(path,
                StaticUtils.getExceptionMessage(e)),
           e);
    }

    return importFromLDIF(clear, reader);
  }



  /**
   * Reads entries from the provided LDIF reader and adds them to the server,
   * optionally clearing any existing entries before beginning to add the new
   * entries.  If an error is encountered while adding entries from LDIF then
   * the server will remain populated with the data it held before the import
   * attempt (even if the {@code clear} is given with a value of {@code true}).
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  clear   Indicates whether to remove all existing entries prior to
   *                 adding entries read from LDIF.
   * @param  reader  The LDIF reader to use to obtain the entries to be
   *                 imported.
   *
   * @return  The number of entries read from LDIF and added to the server.
   *
   * @throws  LDAPException  If a problem occurs while reading entries or adding
   *                         them to the server.
   */
  public int importFromLDIF(final boolean clear, final LDIFReader reader)
         throws LDAPException
  {
    return inMemoryHandler.importFromLDIF(clear, reader);
  }



  /**
   * Writes the current contents of the server in LDIF form to the specified
   * file.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  path                   The path of the file to which the LDIF
   *                                entries should be written.
   * @param  excludeGeneratedAttrs  Indicates whether to exclude automatically
   *                                generated operational attributes like
   *                                entryUUID, entryDN, creatorsName, etc.
   * @param  excludeChangeLog       Indicates whether to exclude entries
   *                                contained in the changelog.
   *
   * @return  The number of entries written to LDIF.
   *
   * @throws  LDAPException  If a problem occurs while writing entries to LDIF.
   */
  public int exportToLDIF(final String path,
                          final boolean excludeGeneratedAttrs,
                          final boolean excludeChangeLog)
         throws LDAPException
  {
    final LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(path);
    }
    catch (final Exception e)
    {
      Debug.debugException(e);
      throw new LDAPException(ResultCode.LOCAL_ERROR,
           ERR_MEM_DS_EXPORT_TO_LDIF_CANNOT_CREATE_WRITER.get(path,
                StaticUtils.getExceptionMessage(e)),
           e);
    }

    return exportToLDIF(ldifWriter, excludeGeneratedAttrs, excludeChangeLog,
         true);
  }



  /**
   * Writes the current contents of the server in LDIF form using the provided
   * LDIF writer.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  ldifWriter             The LDIF writer to use when writing the
   *                                entries.  It must not be {@code null}.
   * @param  excludeGeneratedAttrs  Indicates whether to exclude automatically
   *                                generated operational attributes like
   *                                entryUUID, entryDN, creatorsName, etc.
   * @param  excludeChangeLog       Indicates whether to exclude entries
   *                                contained in the changelog.
   * @param  closeWriter            Indicates whether the LDIF writer should be
   *                                closed after all entries have been written.
   *
   * @return  The number of entries written to LDIF.
   *
   * @throws  LDAPException  If a problem occurs while writing entries to LDIF.
   */
  public int exportToLDIF(final LDIFWriter ldifWriter,
                          final boolean excludeGeneratedAttrs,
                          final boolean excludeChangeLog,
                          final boolean closeWriter)
         throws LDAPException
  {
    return inMemoryHandler.exportToLDIF(ldifWriter, excludeGeneratedAttrs,
         excludeChangeLog, closeWriter);
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public RootDSE getRootDSE()
         throws LDAPException
  {
    return new RootDSE(inMemoryHandler.getEntry(""));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public Schema getSchema()
         throws LDAPException
  {
    return inMemoryHandler.getSchema();
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public Schema getSchema(final String entryDN)
         throws LDAPException
  {
    return inMemoryHandler.getSchema();
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry getEntry(final String dn)
         throws LDAPException
  {
    return searchForEntry(dn, SearchScope.BASE,
         Filter.createPresenceFilter("objectClass"));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry getEntry(final String dn, final String... attributes)
         throws LDAPException
  {
    return searchForEntry(dn, SearchScope.BASE,
         Filter.createPresenceFilter("objectClass"), attributes);
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final String dn, final Attribute... attributes)
         throws LDAPException
  {
    return add(new AddRequest(dn, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final String dn, final Collection<Attribute> attributes)
         throws LDAPException
  {
    return add(new AddRequest(dn, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final Entry entry)
         throws LDAPException
  {
    return add(new AddRequest(entry));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final String... ldifLines)
         throws LDIFException, LDAPException
  {
    return add(new AddRequest(ldifLines));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final AddRequest addRequest)
         throws LDAPException
  {
    final LDAPMessage responseMessage = inMemoryHandler.processAddRequest(1,
         new AddRequestProtocolOp(addRequest.getDN(),
              addRequest.getAttributes()),
         addRequest.getControlList());

    final AddResponseProtocolOp addResponse =
         responseMessage.getAddResponseProtocolOp();

    final LDAPResult ldapResult = new LDAPResult(responseMessage.getMessageID(),
         ResultCode.valueOf(addResponse.getResultCode()),
         addResponse.getDiagnosticMessage(), addResponse.getMatchedDN(),
         addResponse.getReferralURLs(), responseMessage.getControls());

    switch (addResponse.getResultCode())
    {
      case ResultCode.SUCCESS_INT_VALUE:
      case ResultCode.NO_OPERATION_INT_VALUE:
        return ldapResult;
      default:
        throw new LDAPException(ldapResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult add(final ReadOnlyAddRequest addRequest)
         throws LDAPException
  {
    return add(addRequest.duplicate());
  }



  /**
   * Attempts to add all of the provided entries to the server.  If a problem is
   * encountered while attempting to add any of the provided entries, then the
   * server will remain populated with the data it held before this method was
   * called.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  entries  The entries to be added to the server.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to add
   *                         any of the provided entries.
   */
  public void addEntries(final Entry... entries)
         throws LDAPException
  {
    addEntries(Arrays.asList(entries));
  }



  /**
   * Attempts to add all of the provided entries to the server.  If a problem is
   * encountered while attempting to add any of the provided entries, then the
   * server will remain populated with the data it held before this method was
   * called.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  entries  The entries to be added to the server.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to add
   *                         any of the provided entries.
   */
  public void addEntries(final List<? extends Entry> entries)
         throws LDAPException
  {
    inMemoryHandler.addEntries(entries);
  }



  /**
   * Attempts to add a set of entries provided in LDIF form in which each
   * element of the provided array is a line of the LDIF representation, with
   * empty strings as separators between entries (as you would have for blank
   * lines in an LDIF file).  If a problem is encountered while attempting to
   * add any of the provided entries, then the server will remain populated with
   * the data it held before this method was called.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  ldifEntryLines  The lines comprising the LDIF representation of the
   *                         entries to be added.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to add
   *                         any of the provided entries.
   */
  public void addEntries(final String... ldifEntryLines)
         throws LDAPException
  {
    final ByteStringBuffer buffer = new ByteStringBuffer();
    for (final String line : ldifEntryLines)
    {
      buffer.append(line);
      buffer.append(StaticUtils.EOL_BYTES);
    }

    final ArrayList<Entry> entryList = new ArrayList<Entry>(10);
    final LDIFReader reader = new LDIFReader(buffer.asInputStream());
    while (true)
    {
      try
      {
        final Entry entry = reader.readEntry();
        if (entry == null)
        {
          break;
        }
        else
        {
          entryList.add(entry);
        }
      }
      catch (final Exception e)
      {
        Debug.debugException(e);
        throw new LDAPException(ResultCode.PARAM_ERROR,
             ERR_MEM_DS_ADD_ENTRIES_LDIF_PARSE_EXCEPTION.get(
                  StaticUtils.getExceptionMessage(e)),
             e);
      }
    }

    addEntries(entryList);
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public CompareResult compare(final String dn, final String attributeName,
                        final String assertionValue)
         throws LDAPException
  {
    return compare(new CompareRequest(dn, attributeName, assertionValue));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public CompareResult compare(final CompareRequest compareRequest)
         throws LDAPException
  {
    final LDAPMessage responseMessage = inMemoryHandler.processCompareRequest(1,
         new CompareRequestProtocolOp(compareRequest.getDN(),
              compareRequest.getAttributeName(),
              compareRequest.getRawAssertionValue()),
         compareRequest.getControlList());

    final CompareResponseProtocolOp compareResponse =
         responseMessage.getCompareResponseProtocolOp();

    final LDAPResult compareResult = new LDAPResult(
         responseMessage.getMessageID(),
         ResultCode.valueOf(compareResponse.getResultCode()),
         compareResponse.getDiagnosticMessage(), compareResponse.getMatchedDN(),
         compareResponse.getReferralURLs(), responseMessage.getControls());

    switch (compareResponse.getResultCode())
    {
      case ResultCode.COMPARE_TRUE_INT_VALUE:
      case ResultCode.COMPARE_FALSE_INT_VALUE:
        return new CompareResult(compareResult);
      default:
        throw new LDAPException(compareResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public CompareResult compare(final ReadOnlyCompareRequest compareRequest)
         throws LDAPException
  {
    return compare(compareRequest.duplicate());
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult delete(final String dn)
         throws LDAPException
  {
    return delete(new DeleteRequest(dn));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult delete(final DeleteRequest deleteRequest)
         throws LDAPException
  {
    final LDAPMessage responseMessage = inMemoryHandler.processDeleteRequest(1,
         new DeleteRequestProtocolOp(deleteRequest.getDN()),
         deleteRequest.getControlList());

    final DeleteResponseProtocolOp deleteResponse =
         responseMessage.getDeleteResponseProtocolOp();

    final LDAPResult ldapResult = new LDAPResult(responseMessage.getMessageID(),
         ResultCode.valueOf(deleteResponse.getResultCode()),
         deleteResponse.getDiagnosticMessage(), deleteResponse.getMatchedDN(),
         deleteResponse.getReferralURLs(), responseMessage.getControls());

    switch (deleteResponse.getResultCode())
    {
      case ResultCode.SUCCESS_INT_VALUE:
      case ResultCode.NO_OPERATION_INT_VALUE:
        return ldapResult;
      default:
        throw new LDAPException(ldapResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult delete(final ReadOnlyDeleteRequest deleteRequest)
         throws LDAPException
  {
    return delete(deleteRequest.duplicate());
  }



  /**
   * Attempts to delete the specified entry and all entries below it from the
   * server.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  baseDN  The DN of the entry to remove, along with all of its
   *                 subordinates.
   *
   * @return  The number of entries removed from the server, or zero if the
   *          specified entry was not found.
   *
   * @throws  LDAPException  If a problem is encountered while attempting to
   *                         remove the entries.
   */
  public int deleteSubtree(final String baseDN)
         throws LDAPException
  {
    return inMemoryHandler.deleteSubtree(baseDN);
  }



  /**
   * Processes an extended request with the provided request OID.  Note that
   * because some types of extended operations return unusual result codes under
   * "normal" conditions, the server may not always throw an exception for a
   * failed extended operation like it does for other types of operations.  It
   * will throw an exception under conditions where there appears to be a
   * problem with the connection or the server to which the connection is
   * established, but there may be many circumstances in which an extended
   * operation is not processed correctly but this method does not throw an
   * exception.  In the event that no exception is thrown, it is the
   * responsibility of the caller to interpret the result to determine whether
   * the operation was processed as expected.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  requestOID  The OID for the extended request to process.  It must
   *                     not be {@code null}.
   *
   * @return  The extended result object that provides information about the
   *          result of the request processing.  It may or may not indicate that
   *          the operation was successful.
   *
   * @throws  LDAPException  If a problem occurs while sending the request or
   *                         reading the response.
   */
  public ExtendedResult processExtendedOperation(final String requestOID)
         throws LDAPException
  {
    Validator.ensureNotNull(requestOID);

    return processExtendedOperation(new ExtendedRequest(requestOID));
  }



  /**
   * Processes an extended request with the provided request OID and value.
   * Note that because some types of extended operations return unusual result
   * codes under "normal" conditions, the server may not always throw an
   * exception for a failed extended operation like it does for other types of
   * operations.  It will throw an exception under conditions where there
   * appears to be a problem with the connection or the server to which the
   * connection is established, but there may be many circumstances in which an
   * extended operation is not processed correctly but this method does not
   * throw an exception.  In the event that no exception is thrown, it is the
   * responsibility of the caller to interpret the result to determine whether
   * the operation was processed as expected.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  requestOID    The OID for the extended request to process.  It must
   *                       not be {@code null}.
   * @param  requestValue  The encoded value for the extended request to
   *                       process.  It may be {@code null} if there does not
   *                       need to be a value for the requested operation.
   *
   * @return  The extended result object that provides information about the
   *          result of the request processing.  It may or may not indicate that
   *          the operation was successful.
   *
   * @throws  LDAPException  If a problem occurs while sending the request or
   *                         reading the response.
   */
  public ExtendedResult processExtendedOperation(final String requestOID,
                             final ASN1OctetString requestValue)
         throws LDAPException
  {
    Validator.ensureNotNull(requestOID);

    return processExtendedOperation(new ExtendedRequest(requestOID,
         requestValue));
  }



  /**
   * Processes the provided extended request.  Note that because some types of
   * extended operations return unusual result codes under "normal" conditions,
   * the server may not always throw an exception for a failed extended
   * operation like it does for other types of operations.  It will throw an
   * exception under conditions where there appears to be a problem with the
   * connection or the server to which the connection is established, but there
   * may be many circumstances in which an extended operation is not processed
   * correctly but this method does not throw an exception.  In the event that
   * no exception is thrown, it is the responsibility of the caller to interpret
   * the result to determine whether the operation was processed as expected.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  extendedRequest  The extended request to be processed.  It must not
   *                          be {@code null}.
   *
   * @return  The extended result object that provides information about the
   *          result of the request processing.  It may or may not indicate that
   *          the operation was successful.
   *
   * @throws  LDAPException  If a problem occurs while sending the request or
   *                         reading the response.
   */
  public ExtendedResult processExtendedOperation(
                               final ExtendedRequest extendedRequest)
         throws LDAPException
  {
    Validator.ensureNotNull(extendedRequest);

    final LDAPMessage responseMessage =
         inMemoryHandler.processExtendedRequest(1,
              new ExtendedRequestProtocolOp(extendedRequest.getOID(),
                   extendedRequest.getValue()),
              extendedRequest.getControlList());

    final ExtendedResponseProtocolOp extendedResponse =
         responseMessage.getExtendedResponseProtocolOp();

    final ResultCode rc = ResultCode.valueOf(extendedResponse.getResultCode());

    final String[] referralURLs;
    final List<String> referralURLList = extendedResponse.getReferralURLs();
    if ((referralURLList == null) || referralURLList.isEmpty())
    {
      referralURLs = StaticUtils.NO_STRINGS;
    }
    else
    {
      referralURLs = new String[referralURLList.size()];
      referralURLList.toArray(referralURLs);
    }

    final Control[] responseControls;
    final List<Control> controlList = responseMessage.getControls();
    if ((controlList == null) || controlList.isEmpty())
    {
      responseControls = StaticUtils.NO_CONTROLS;
    }
    else
    {
      responseControls = new Control[controlList.size()];
      controlList.toArray(responseControls);
    }

    final ExtendedResult extendedResult = new ExtendedResult(
         responseMessage.getMessageID(), rc,
         extendedResponse.getDiagnosticMessage(),
         extendedResponse.getMatchedDN(), referralURLs,
         extendedResponse.getResponseOID(),
         extendedResponse.getResponseValue(), responseControls);

    if ((extendedResult.getOID() == null) &&
        (extendedResult.getValue() == null))
    {
      switch (rc.intValue())
      {
        case ResultCode.OPERATIONS_ERROR_INT_VALUE:
        case ResultCode.PROTOCOL_ERROR_INT_VALUE:
        case ResultCode.BUSY_INT_VALUE:
        case ResultCode.UNAVAILABLE_INT_VALUE:
        case ResultCode.OTHER_INT_VALUE:
        case ResultCode.SERVER_DOWN_INT_VALUE:
        case ResultCode.LOCAL_ERROR_INT_VALUE:
        case ResultCode.ENCODING_ERROR_INT_VALUE:
        case ResultCode.DECODING_ERROR_INT_VALUE:
        case ResultCode.TIMEOUT_INT_VALUE:
        case ResultCode.NO_MEMORY_INT_VALUE:
        case ResultCode.CONNECT_ERROR_INT_VALUE:
          throw new LDAPException(extendedResult);
      }
    }

    return extendedResult;
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final String dn, final Modification mod)
         throws LDAPException
  {
    return modify(new ModifyRequest(dn, mod));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final String dn, final Modification... mods)
         throws LDAPException
  {
    return modify(new ModifyRequest(dn, mods));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final String dn, final List<Modification> mods)
         throws LDAPException
  {
    return modify(new ModifyRequest(dn, mods));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final String... ldifModificationLines)
         throws LDIFException, LDAPException
  {
    return modify(new ModifyRequest(ldifModificationLines));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final ModifyRequest modifyRequest)
         throws LDAPException
  {
    final LDAPMessage responseMessage = inMemoryHandler.processModifyRequest(1,
         new ModifyRequestProtocolOp(modifyRequest.getDN(),
              modifyRequest.getModifications()),
         modifyRequest.getControlList());

    final ModifyResponseProtocolOp modifyResponse =
         responseMessage.getModifyResponseProtocolOp();

    final LDAPResult ldapResult = new LDAPResult(responseMessage.getMessageID(),
         ResultCode.valueOf(modifyResponse.getResultCode()),
         modifyResponse.getDiagnosticMessage(), modifyResponse.getMatchedDN(),
         modifyResponse.getReferralURLs(), responseMessage.getControls());

    switch (modifyResponse.getResultCode())
    {
      case ResultCode.SUCCESS_INT_VALUE:
      case ResultCode.NO_OPERATION_INT_VALUE:
        return ldapResult;
      default:
        throw new LDAPException(ldapResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modify(final ReadOnlyModifyRequest modifyRequest)
         throws LDAPException
  {
    return modify(modifyRequest.duplicate());
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modifyDN(final String dn, final String newRDN,
                             final boolean deleteOldRDN)
         throws LDAPException
  {
    return modifyDN(new ModifyDNRequest(dn, newRDN, deleteOldRDN));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modifyDN(final String dn, final String newRDN,
                             final boolean deleteOldRDN,
                             final String newSuperiorDN)
         throws LDAPException
  {
    return modifyDN(new ModifyDNRequest(dn, newRDN, deleteOldRDN,
         newSuperiorDN));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modifyDN(final ModifyDNRequest modifyDNRequest)
         throws LDAPException
  {
    final LDAPMessage responseMessage = inMemoryHandler.processModifyDNRequest(
         1, new ModifyDNRequestProtocolOp(modifyDNRequest.getDN(),
              modifyDNRequest.getNewRDN(), modifyDNRequest.deleteOldRDN(),
              modifyDNRequest.getNewSuperiorDN()),
         modifyDNRequest.getControlList());

    final ModifyDNResponseProtocolOp modifyDNResponse =
         responseMessage.getModifyDNResponseProtocolOp();

    final LDAPResult ldapResult = new LDAPResult(responseMessage.getMessageID(),
         ResultCode.valueOf(modifyDNResponse.getResultCode()),
         modifyDNResponse.getDiagnosticMessage(),
         modifyDNResponse.getMatchedDN(), modifyDNResponse.getReferralURLs(),
         responseMessage.getControls());

    switch (modifyDNResponse.getResultCode())
    {
      case ResultCode.SUCCESS_INT_VALUE:
      case ResultCode.NO_OPERATION_INT_VALUE:
        return ldapResult;
      default:
        throw new LDAPException(ldapResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public LDAPResult modifyDN(final ReadOnlyModifyDNRequest modifyDNRequest)
         throws LDAPException
  {
    return modifyDN(modifyDNRequest.duplicate());
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final String baseDN, final SearchScope scope,
                             final String filter, final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(baseDN, scope, parseFilter(filter),
         attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final String baseDN, final SearchScope scope,
                             final Filter filter, final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(baseDN, scope, filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final SearchResultListener searchResultListener,
                             final String baseDN, final SearchScope scope,
                             final String filter, final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(searchResultListener, baseDN, scope,
         parseFilter(filter), attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final SearchResultListener searchResultListener,
                             final String baseDN, final SearchScope scope,
                             final Filter filter, final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(searchResultListener, baseDN, scope,
         filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final String baseDN, final SearchScope scope,
                             final DereferencePolicy derefPolicy,
                             final int sizeLimit, final int timeLimit,
                             final boolean typesOnly, final String filter,
                             final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(baseDN, scope, derefPolicy, sizeLimit,
         timeLimit, typesOnly, parseFilter(filter), attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final String baseDN, final SearchScope scope,
                             final DereferencePolicy derefPolicy,
                             final int sizeLimit, final int timeLimit,
                             final boolean typesOnly, final Filter filter,
                             final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(baseDN, scope, derefPolicy, sizeLimit,
         timeLimit, typesOnly, filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final SearchResultListener searchResultListener,
                             final String baseDN, final SearchScope scope,
                             final DereferencePolicy derefPolicy,
                             final int sizeLimit, final int timeLimit,
                             final boolean typesOnly, final String filter,
                             final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(searchResultListener, baseDN, scope,
         derefPolicy, sizeLimit, timeLimit, typesOnly, parseFilter(filter),
         attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final SearchResultListener searchResultListener,
                             final String baseDN, final SearchScope scope,
                             final DereferencePolicy derefPolicy,
                             final int sizeLimit, final int timeLimit,
                             final boolean typesOnly, final Filter filter,
                             final String... attributes)
         throws LDAPSearchException
  {
    return search(new SearchRequest(searchResultListener, baseDN, scope,
         derefPolicy, sizeLimit, timeLimit, typesOnly, filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final SearchRequest searchRequest)
         throws LDAPSearchException
  {
    final List<SearchResultEntry> entryList =
         new ArrayList<SearchResultEntry>(10);
    final List<SearchResultReference> referenceList =
         new ArrayList<SearchResultReference>(10);

    final LDAPMessage responseMessage = inMemoryHandler.processSearchRequest(1,
         new SearchRequestProtocolOp(searchRequest.getBaseDN(),
              searchRequest.getScope(), searchRequest.getDereferencePolicy(),
              searchRequest.getSizeLimit(), searchRequest.getTimeLimitSeconds(),
              searchRequest.typesOnly(), searchRequest.getFilter(),
              searchRequest.getAttributeList()),
         searchRequest.getControlList(), entryList, referenceList);


    final List<SearchResultEntry> returnEntryList;
    final List<SearchResultReference> returnReferenceList;
    final SearchResultListener searchListener =
         searchRequest.getSearchResultListener();
    if (searchListener == null)
    {
      returnEntryList = Collections.unmodifiableList(entryList);
      returnReferenceList = Collections.unmodifiableList(referenceList);
    }
    else
    {
      returnEntryList     = null;
      returnReferenceList = null;

      for (final SearchResultEntry e : entryList)
      {
        searchListener.searchEntryReturned(e);
      }

      for (final SearchResultReference r : referenceList)
      {
        searchListener.searchReferenceReturned(r);
      }
    }


    final SearchResultDoneProtocolOp searchDone =
         responseMessage.getSearchResultDoneProtocolOp();

    final ResultCode rc = ResultCode.valueOf(searchDone.getResultCode());

    final String[] referralURLs;
    final List<String> referralURLList = searchDone.getReferralURLs();
    if ((referralURLList == null) || referralURLList.isEmpty())
    {
      referralURLs = StaticUtils.NO_STRINGS;
    }
    else
    {
      referralURLs = new String[referralURLList.size()];
      referralURLList.toArray(referralURLs);
    }

    final Control[] responseControls;
    final List<Control> controlList = responseMessage.getControls();
    if ((controlList == null) || controlList.isEmpty())
    {
      responseControls = StaticUtils.NO_CONTROLS;
    }
    else
    {
      responseControls = new Control[controlList.size()];
      controlList.toArray(responseControls);
    }

    final SearchResult searchResult =new SearchResult(
         responseMessage.getMessageID(), rc, searchDone.getDiagnosticMessage(),
         searchDone.getMatchedDN(), referralURLs, returnEntryList,
         returnReferenceList, entryList.size(), referenceList.size(),
         responseControls);

    if (rc == ResultCode.SUCCESS)
    {
      return searchResult;
    }
    else
    {
      throw new LDAPSearchException(searchResult);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResult search(final ReadOnlySearchRequest searchRequest)
         throws LDAPSearchException
  {
    return search(searchRequest.duplicate());
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(final String baseDN,
                                          final SearchScope scope,
                                          final String filter,
                                          final String... attributes)
         throws LDAPSearchException
  {
    return searchForEntry(new SearchRequest(baseDN, scope, parseFilter(filter),
         attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(final String baseDN,
                                          final SearchScope scope,
                                          final Filter filter,
                                          final String... attributes)
         throws LDAPSearchException
  {
    return searchForEntry(new SearchRequest(baseDN, scope, filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(final String baseDN,
                                          final SearchScope scope,
                                          final DereferencePolicy derefPolicy,
                                          final int timeLimit,
                                          final boolean typesOnly,
                                          final String filter,
                                          final String... attributes)
         throws LDAPSearchException
  {
    return searchForEntry(new SearchRequest(baseDN, scope, derefPolicy, 1,
         timeLimit, typesOnly, parseFilter(filter), attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(final String baseDN,
                                          final SearchScope scope,
                                          final DereferencePolicy derefPolicy,
                                          final int timeLimit,
                                          final boolean typesOnly,
                                          final Filter filter,
                                          final String... attributes)
         throws LDAPSearchException
  {
    return searchForEntry(new SearchRequest(baseDN, scope, derefPolicy, 1,
         timeLimit, typesOnly, filter, attributes));
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(final SearchRequest searchRequest)
         throws LDAPSearchException
  {
    final SearchRequest r;
    if ((searchRequest.getSizeLimit() == 1) &&
        (searchRequest.getSearchResultListener() == null))
    {
      r = searchRequest;
    }
    else
    {
      r = new SearchRequest(searchRequest.getBaseDN(), searchRequest.getScope(),
           searchRequest.getDereferencePolicy(), 1,
           searchRequest.getTimeLimitSeconds(), searchRequest.typesOnly(),
           searchRequest.getFilter(), searchRequest.getAttributes());

      r.setFollowReferrals(InternalSDKHelper.followReferralsInternal(r));
      r.setResponseTimeoutMillis(searchRequest.getResponseTimeoutMillis(null));

      if (searchRequest.hasControl())
      {
        r.setControls(searchRequest.getControls());
      }
    }

    final SearchResult result;
    try
    {
      result = search(r);
    }
    catch (final LDAPSearchException lse)
    {
      Debug.debugException(lse);

      if (lse.getResultCode() == ResultCode.NO_SUCH_OBJECT)
      {
        return null;
      }

      throw lse;
    }

    if (result.getEntryCount() == 0)
    {
      return null;
    }
    else
    {
      return result.getSearchEntries().get(0);
    }
  }



  /**
   * {@inheritDoc}
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   */
  public SearchResultEntry searchForEntry(
                                final ReadOnlySearchRequest searchRequest)
         throws LDAPSearchException
  {
    return searchForEntry(searchRequest.duplicate());
  }



  /**
   * Parses the provided string as a search filter.
   *
   * @param  s  The string to be parsed.
   *
   * @return  The parsed filter.
   *
   * @throws  LDAPSearchException  If the provided string could not be parsed as
   *                               a valid search filter.
   */
  private static Filter parseFilter(final String s)
          throws LDAPSearchException
  {
    try
    {
      return Filter.create(s);
    }
    catch (final LDAPException le)
    {
      throw new LDAPSearchException(le);
    }
  }



  /**
   * Indicates whether the specified entry exists in the server.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn  The DN of the entry for which to make the determination.
   *
   * @return  {@code true} if the entry exists, or {@code false} if not.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public boolean entryExists(final String dn)
         throws LDAPException
  {
    return inMemoryHandler.entryExists(dn);
  }



  /**
   * Indicates whether the specified entry exists in the server and matches the
   * given filter.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn      The DN of the entry for which to make the determination.
   * @param  filter  The filter the entry is expected to match.
   *
   * @return  {@code true} if the entry exists and matches the specified filter,
   *          or {@code false} if not.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public boolean entryExists(final String dn, final String filter)
         throws LDAPException
  {
    return inMemoryHandler.entryExists(dn, filter);
  }



  /**
   * Indicates whether the specified entry exists in the server.  This will
   * return {@code true} only if the target entry exists and contains all values
   * for all attributes of the provided entry.  The entry will be allowed to
   * have attribute values not included in the provided entry.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  entry  The entry to compare against the directory server.
   *
   * @return  {@code true} if the entry exists in the server and is a superset
   *          of the provided entry, or {@code false} if not.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public boolean entryExists(final Entry entry)
         throws LDAPException
  {
    return inMemoryHandler.entryExists(entry);
  }



  /**
   * Ensures that an entry with the provided DN exists in the directory.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn  The DN of the entry for which to make the determination.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist.
   */
  public void assertEntryExists(final String dn)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntryExists(dn);
  }



  /**
   * Ensures that an entry with the provided DN exists in the directory.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn      The DN of the entry for which to make the determination.
   * @param  filter  A filter that the target entry must match.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist or does not
   *                          match the provided filter.
   */
  public void assertEntryExists(final String dn, final String filter)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntryExists(dn, filter);
  }



  /**
   * Ensures that an entry exists in the directory with the same DN and all
   * attribute values contained in the provided entry.  The server entry may
   * contain additional attributes and/or attribute values not included in the
   * provided entry.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  entry  The entry expected to be present in the directory server.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist or does not
   *                          match the provided filter.
   */
  public void assertEntryExists(final Entry entry)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntryExists(entry);
  }



  /**
   * Retrieves a list containing the DNs of the entries which are missing from
   * the directory server.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dns  The DNs of the entries to try to find in the server.
   *
   * @return  A list containing all of the provided DNs that were not found in
   *          the server, or an empty list if all entries were found.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingEntryDNs(final String... dns)
         throws LDAPException
  {
    return inMemoryHandler.getMissingEntryDNs(StaticUtils.toList(dns));
  }



  /**
   * Retrieves a list containing the DNs of the entries which are missing from
   * the directory server.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dns  The DNs of the entries to try to find in the server.
   *
   * @return  A list containing all of the provided DNs that were not found in
   *          the server, or an empty list if all entries were found.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingEntryDNs(final Collection<String> dns)
         throws LDAPException
  {
    return inMemoryHandler.getMissingEntryDNs(dns);
  }



  /**
   * Ensures that all of the entries with the provided DNs exist in the
   * directory.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dns  The DNs of the entries for which to make the determination.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If any of the target entries does not exist.
   */
  public void assertEntriesExist(final String... dns)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntriesExist(StaticUtils.toList(dns));
  }



  /**
   * Ensures that all of the entries with the provided DNs exist in the
   * directory.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dns  The DNs of the entries for which to make the determination.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If any of the target entries does not exist.
   */
  public void assertEntriesExist(final Collection<String> dns)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntriesExist(dns);
  }



  /**
   * Retrieves a list containing all of the named attributes which do not exist
   * in the target entry.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry to examine.
   * @param  attributeNames  The names of the attributes expected to be present
   *                         in the target entry.
   *
   * @return  A list containing the names of the attributes which were not
   *          present in the target entry, an empty list if all specified
   *          attributes were found in the entry, or {@code null} if the target
   *          entry does not exist.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingAttributeNames(final String dn,
                                               final String... attributeNames)
         throws LDAPException
  {
    return inMemoryHandler.getMissingAttributeNames(dn,
         StaticUtils.toList(attributeNames));
  }



  /**
   * Retrieves a list containing all of the named attributes which do not exist
   * in the target entry.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry to examine.
   * @param  attributeNames  The names of the attributes expected to be present
   *                         in the target entry.
   *
   * @return  A list containing the names of the attributes which were not
   *          present in the target entry, an empty list if all specified
   *          attributes were found in the entry, or {@code null} if the target
   *          entry does not exist.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingAttributeNames(final String dn,
                           final Collection<String> attributeNames)
         throws LDAPException
  {
    return inMemoryHandler.getMissingAttributeNames(dn, attributeNames);
  }



  /**
   * Ensures that the specified entry exists in the directory with all of the
   * specified attributes.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry to examine.
   * @param  attributeNames  The names of the attributes that are expected to be
   *                         present in the provided entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist or does not
   *                          contain all of the specified attributes.
   */
  public void assertAttributeExists(final String dn,
                                    final String... attributeNames)
        throws LDAPException, AssertionError
  {
    inMemoryHandler.assertAttributeExists(dn,
         StaticUtils.toList(attributeNames));
  }



  /**
   * Ensures that the specified entry exists in the directory with all of the
   * specified attributes.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry to examine.
   * @param  attributeNames  The names of the attributes that are expected to be
   *                         present in the provided entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist or does not
   *                          contain all of the specified attributes.
   */
  public void assertAttributeExists(final String dn,
                                    final Collection<String> attributeNames)
        throws LDAPException, AssertionError
  {
    inMemoryHandler.assertAttributeExists(dn, attributeNames);
  }



  /**
   * Retrieves a list of all provided attribute values which are missing from
   * the specified entry.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry to examine.
   * @param  attributeName    The attribute expected to be present in the target
   *                          entry with the given values.
   * @param  attributeValues  The values expected to be present in the target
   *                          entry.
   *
   * @return  A list containing all of the provided values which were not found
   *          in the entry, an empty list if all provided attribute values were
   *          found, or {@code null} if the target entry does not exist.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingAttributeValues(final String dn,
                                                final String attributeName,
                                                final String... attributeValues)
         throws LDAPException
  {
    return inMemoryHandler.getMissingAttributeValues(dn, attributeName,
         StaticUtils.toList(attributeValues));
  }



  /**
   * Retrieves a list of all provided attribute values which are missing from
   * the specified entry.  The target attribute may or may not contain
   * additional values.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry to examine.
   * @param  attributeName    The attribute expected to be present in the target
   *                          entry with the given values.
   * @param  attributeValues  The values expected to be present in the target
   *                          entry.
   *
   * @return  A list containing all of the provided values which were not found
   *          in the entry, an empty list if all provided attribute values were
   *          found, or {@code null} if the target entry does not exist.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   */
  public List<String> getMissingAttributeValues(final String dn,
                           final String attributeName,
                           final Collection<String> attributeValues)
       throws LDAPException
  {
    return inMemoryHandler.getMissingAttributeValues(dn, attributeName,
         attributeValues);
  }



  /**
   * Ensures that the specified entry exists in the directory with all of the
   * specified values for the given attribute.  The attribute may or may not
   * contain additional values.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry to examine.
   * @param  attributeName    The name of the attribute to examine.
   * @param  attributeValues  The set of values which must exist for the given
   *                          attribute.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist, does not
   *                          contain the specified attribute, or that attribute
   *                          does not have all of the specified values.
   */
  public void assertValueExists(final String dn, final String attributeName,
                                final String... attributeValues)
        throws LDAPException, AssertionError
  {
    inMemoryHandler.assertValueExists(dn, attributeName,
         StaticUtils.toList(attributeValues));
  }



  /**
   * Ensures that the specified entry exists in the directory with all of the
   * specified values for the given attribute.  The attribute may or may not
   * contain additional values.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry to examine.
   * @param  attributeName    The name of the attribute to examine.
   * @param  attributeValues  The set of values which must exist for the given
   *                          attribute.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry does not exist, does not
   *                          contain the specified attribute, or that attribute
   *                          does not have all of the specified values.
   */
  public void assertValueExists(final String dn, final String attributeName,
                                final Collection<String> attributeValues)
        throws LDAPException, AssertionError
  {
    inMemoryHandler.assertValueExists(dn, attributeName, attributeValues);
  }



  /**
   * Ensures that the specified entry does not exist in the directory.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn  The DN of the entry expected to be missing.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry is found in the server.
   */
  public void assertEntryMissing(final String dn)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertEntryMissing(dn);
  }



  /**
   * Ensures that the specified entry exists in the directory but does not
   * contain any of the specified attributes.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry expected to be present.
   * @param  attributeNames  The names of the attributes expected to be missing
   *                         from the entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry is missing from the server, or
   *                          if it contains any of the target attributes.
   */
  public void assertAttributeMissing(final String dn,
                                     final String... attributeNames)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertAttributeMissing(dn,
         StaticUtils.toList(attributeNames));
  }



  /**
   * Ensures that the specified entry exists in the directory but does not
   * contain any of the specified attributes.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn              The DN of the entry expected to be present.
   * @param  attributeNames  The names of the attributes expected to be missing
   *                         from the entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry is missing from the server, or
   *                          if it contains any of the target attributes.
   */
  public void assertAttributeMissing(final String dn,
                                     final Collection<String> attributeNames)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertAttributeMissing(dn, attributeNames);
  }



  /**
   * Ensures that the specified entry exists in the directory but does not
   * contain any of the specified attribute values.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry expected to be present.
   * @param  attributeName    The name of the attribute to examine.
   * @param  attributeValues  The values expected to be missing from the target
   *                          entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry is missing from the server, or
   *                          if it contains any of the target attribute values.
   */
  public void assertValueMissing(final String dn, final String attributeName,
                                 final String... attributeValues)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertValueMissing(dn, attributeName,
         StaticUtils.toList(attributeValues));
  }



  /**
   * Ensures that the specified entry exists in the directory but does not
   * contain any of the specified attribute values.
   * <BR><BR>
   * This method may be used regardless of whether the server is listening for
   * client connections.
   *
   * @param  dn               The DN of the entry expected to be present.
   * @param  attributeName    The name of the attribute to examine.
   * @param  attributeValues  The values expected to be missing from the target
   *                          entry.
   *
   * @throws  LDAPException  If a problem is encountered while trying to
   *                         communicate with the directory server.
   *
   * @throws  AssertionError  If the target entry is missing from the server, or
   *                          if it contains any of the target attribute values.
   */
  public void assertValueMissing(final String dn, final String attributeName,
                                 final Collection<String> attributeValues)
         throws LDAPException, AssertionError
  {
    inMemoryHandler.assertValueMissing(dn, attributeName, attributeValues);
  }
}
