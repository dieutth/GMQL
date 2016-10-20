package it.polimi.genomics.importer.GMQLImporter.Defaults

import it.polimi.genomics.importer.GMQLImporter.{GMQLDownloader,GMQLSource,GMQLDataset}
import it.polimi.genomics.importer.GMQLImporter.utils.SCHEMA_LOCATION
import it.polimi.genomics.importer.FileLogger.FileLogger
import it.polimi.genomics.importer.GMQLImporter.Defaults.utils.FTP

/**
  * Created by Nacho on 10/13/16.
  */
object FTPDownloader extends GMQLDownloader {

  /**
    * downloads the files from the source defined in the information
    * into the folder defined in the information
    *
    * @param source contains specific download and sorting info.
    */
  override def download(source: GMQLSource): Unit ={
    println("Starting download for: "+source.url)
    if (!new java.io.File(source.outputFolder).exists) {
      new java.io.File(source.outputFolder).mkdirs()
    }
    val ftp = new FTP()

    //the mark to compare is done here because the iteration on ftp is based on ftp folders and not
    //on the source datasets.
    source.datasets.foreach(dataset=>{
      val outputPath = source.outputFolder + "/" + dataset.outputFolder + "/Downloads"
      val log = new FileLogger(outputPath)
      log.markToCompare()
      log.saveTable()
    })

    if(ftp.connectWithAuth(source.url, source.parameters.filter(_._1 == "username").head._2 , source.parameters.filter(_._1 == "password").head._2).getOrElse(false)){
      recursiveDownload(ftp,source)
      ftp.disconnect()

      source.datasets.foreach(dataset=>{
        val outputPath = source.outputFolder + "/" + dataset.outputFolder + "/Downloads"
        val log = new FileLogger(outputPath)
        log.markAsOutdated()
        log.saveTable()
      })
    }
    else
      println("ftp connection with "+source.url+" couldn't be handled.","username:"+source.parameters.filter(_._1 == "username").head._2,"password:"+source.parameters.filter(_._1 == "password").head._2)

  }
  /**
    * recursively checks all folders and subfolders matching with the regular expressions defined in the information
    *
    * @param ftp current state of the ftp connection
    * @param source configuration for the downloader, folders for input and output by regex and also for files.
    */
  private def recursiveDownload(ftp: FTP, source: GMQLSource): Unit = {
    checkFolderForDownloads(ftp,source)
    downloadSubfolders(ftp,source)
  }

  /**
    * given a folder, searches all the possible links to download and downloads if signaled by Updater and information
    * puts all content into information.outputFolder/dataset.outputFolder/Downloads/ (this is because TCGA2BED does no transform and we dont want just to copy the files).
    * @param ftp current state of the ftp connection
    * @param source configuration for downloader, folders for input and output by regex and also for files
    */
  private def checkFolderForDownloads(ftp: FTP, source: GMQLSource): Unit = {
    for (dataset <- source.datasets) {
      if (ftp.workingDirectory().matches(dataset.parameters.filter(_._1 == "folder_regex").head._2)) {
        val outputPath = source.outputFolder + "/" + dataset.outputFolder + "/Downloads"
        val log = new FileLogger(outputPath)

        println("Directory: " + ftp.workingDirectory())
        if (!new java.io.File(outputPath).exists) {
          new java.io.File(outputPath).mkdirs()
        }

        val files = ftp.listFiles().filter(_.isFile).filter(_.getName.matches(dataset.parameters.filter(_._1 == "files_regex").head._2))

        for (file <- files) {
          if (log.checkIfUpdate(
            file.getName,
            ftp.workingDirectory() + "/" + file.getName,
            file.getSize.toString,
            file.getTimestamp.getTime.toString)) {

            print("Downloading: " + ftp.workingDirectory() + "/" + file.getName)
            var downloaded = ftp.downloadFile(file.getName, outputPath + "/" + file.getName)
            var timesTried = 0
            while (!downloaded && timesTried < 4) {
              downloaded = ftp.downloadFile(file.getName, outputPath + "/" + file.getName)
              timesTried += 1
            }
            if (!downloaded) {
              println(" FAILED")
              log.markAsFailed(file.getName)
            }
            else {
              println(" DONE")
              log.markAsUpdated(file.getName)
            }
          }
        }
        log.saveTable()
      }
    }
  }

  /**
    * Finds all subfolders in the working directory and performs checkFolderForDownloads on it
    * @param ftp current state of the ftp connection
    * @param source configuration for downloader, folders for input and output by regex and also for files
    */
  private def downloadSubfolders(ftp: FTP, source:GMQLSource): Unit ={
    ftp.listDirectories().foreach(directory => {
      ftp.cd(directory.getName)
      recursiveDownload(ftp, source)
      ftp.cd("..")
    })
  }
  /**
    * this method is meant to be used for downloading ftp schema
    * puts the schema in a folder called "downloadedSchema" with chosen name
    *
    * maybe I have to change the information parameter.
    * and maybe the place to do this is not here.
    *
    * @param source configuration for downloader, folders for input and output by regex and also for files
    * @param dataset contains information for parameters of the url
    */
  def downloadSchema(source: GMQLSource, dataset: GMQLDataset): Unit ={
    if (!new java.io.File(source.outputFolder).exists) {
      new java.io.File(source.outputFolder).mkdirs()
    }
    val ftp = new FTP()
    if(dataset.schemaLocation == SCHEMA_LOCATION.REMOTE && ftp.connectWithAuth(source.url, source.parameters.filter(_._1 == "username").head._2 , source.parameters.filter(_._1 == "password").head._2).getOrElse(false)){
      if(!ftp.downloadFile(dataset.schema,source.outputFolder+"/"+dataset.outputFolder+"/"+dataset.outputFolder+".schema"))
        println("File: " + dataset.schema + " Couldn't be downloaded")
      ftp.disconnect()
    }
    else
      println("ftp connection with "+source.url+" couldn't be handled.","username:"+source.parameters.filter(_._1 == "username").head._2,"password:"+source.parameters.filter(_._1 == "password").head._2)
  }
  def printTest(): Unit ={
    println("hello from FTPDownloader"+ this.getClass.toString)
  }
}
