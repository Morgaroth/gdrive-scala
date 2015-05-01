package io.github.morgaroth.gdrivesync.api

import java.io._
import java.util
import java.util.Properties

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.DriveScopes.{DRIVE, DRIVE_APPDATA, DRIVE_METADATA}

import scala.collection.JavaConverters._
import scala.compat.Platform
import scala.io.{Source, StdIn}

object Auth {
  private val credentialFile = "/home/mateusz/projects/gdrive-scala/creds.json"
  private val credentialsStorage = s"${System.getProperty("user.home")}/.google_credentials_store"

  val jacksonFactory: JacksonFactory = JacksonFactory.getDefaultInstance
  val transport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
//  private val credentials = Source.fromFile(credentialFile).getLines().mkString
  private val credentials = """{
                              |  "installed": {
                              |    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                              |    "client_secret": "jKKfMrtBgHyqQA3M3BMom5Nx",
                              |    "token_uri": "https://accounts.google.com/o/oauth2/token",
                              |    "client_email": "",
                              |    "redirect_uris": [
                              |      "urn:ietf:wg:oauth:2.0:oob"
                              |    ],
                              |    "client_x509_cert_url": "",
                              |    "client_id": "848652324921-djrqlu03tliuaj465mb0foelqo960etv.apps.googleusercontent.com",
                              |    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
                              |  }
                              |}""".stripMargin


  private lazy val internalAuthorize: GoogleAuthorizationCodeFlow = authorize(new ByteArrayInputStream(credentials.getBytes))

  def tryLoadPreviouslySaved(userId: String = "user") = Option(internalAuthorize.loadCredential(userId))

  def authorizeUser(userID: String = "user"): Option[Credential] =
    Auth.tryLoadPreviouslySaved(userID).orElse {
      println(Auth.authorizeRequestURL)
      val token = StdIn.readLine(s"Please copy abowe link, paste to Internet browser and allow ${AppName.name} to access Your Google Drive. Then copy token code, were shown, and paste here.")
      Auth.createToken(token, userID)
    }

  def authorizeRequestURL: String = {
    internalAuthorize.newAuthorizationUrl()
      .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
      .build()
  }

  def createToken(token: String, userId: String = "user"): Option[Credential] = {
    val tokenResponse = internalAuthorize.newTokenRequest(token)
      .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
      .execute()
    internalAuthorize.createAndStoreCredential(tokenResponse, userId)
    tryLoadPreviouslySaved(userId)
  }

  private def authorize(data: InputStream): GoogleAuthorizationCodeFlow = {
    val clientSecrets = GoogleClientSecrets.load(jacksonFactory, new InputStreamReader(data))
    val permissions: util.List[String] = List(DRIVE, DRIVE_METADATA).asJava
    val flow = new GoogleAuthorizationCodeFlow.Builder(transport, jacksonFactory, clientSecrets, permissions)
      .setDataStoreFactory(new FileDataStoreFactory(new File(credentialsStorage)))
      .build()
    flow
  }

}