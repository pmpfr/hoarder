
/*
 * Hoarder - Cached compilation plugin for sbt.
 * Copyright 2016 - 2017, Krzysztof Romanowski
 * This software is released under the terms written in LICENSE.
 */

package org.romanowski.hoarder.core

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.nio.file.Paths

import sbt.Attributed
import sbt.IO
import sbt.Keys._
import sbt.internal.inc.Stamp
import sbt.internal.inc.Stamper
import xsbti.compile.MiniSetup
import xsbti.compile.analysis._



case class SbtAnalysisMapper(sbtOutput: Path,
                        sourceRoots: Seq[Path],
                        projectRoot: Path,
                        classpath: Seq[Attributed[File]],
                        includeSize: Boolean = false) {


  val mappers: ReadWriteMappers  = new ReadWriteMappers(ReadMapper, WriteMapper)

  private val header = "##"
  private val headerPath = Paths.get("##")

  private implicit class PathOpts(mainPath: Path){
    def relativize(f: File) = {
      val p = f.toPath
      if (p.startsWith(mainPath))
        new File(header, mainPath.relativize(p).toString)
      else f
    }

    def derelativize(f: File) = {
      val p = f.toPath
      if (p.startsWith(headerPath))
        mainPath.resolve(headerPath.relativize(p)).toFile
      else f
    }
  }

  private object WriteMapper extends WriteMapper {
    override def mapSourceFile(sourceFile: File): File = projectRoot.relativize(sourceFile)
    override def mapBinaryFile(binaryFile: File): File = exportBinaryFile(binaryFile)
    override def mapProductFile(productFile: File): File = sbtOutput.relativize(productFile)

    override def mapOutputDir(outputDir: File): File = sbtOutput.relativize(outputDir)
    override def mapSourceDir(sourceDir: File): File = projectRoot.relativize(sourceDir)

    override def mapClasspathEntry(classpathEntry: File): File = classpathEntry //TODO fix this?

    override def mapJavacOption(javacOption: String): String = javacOption
    override def mapScalacOption(scalacOption: String): String = scalacOption

    override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = binaryStamp
    override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = writeSourceStamp(file, sourceStamp)
    override def mapProductStamp(file: File, productStamp: Stamp): Stamp = productStamp

    override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
  }

  private object ReadMapper extends ReadMapper {
    override def mapSourceFile(sourceFile: File): File = projectRoot.derelativize(sourceFile)
    override def mapBinaryFile(binaryFile: File): File = importBinaryFile(binaryFile)
    override def mapProductFile(productFile: File): File = sbtOutput.derelativize(productFile)

    override def mapOutputDir(outputDir: File): File = sbtOutput.derelativize(outputDir)
    override def mapSourceDir(sourceDir: File): File = projectRoot.derelativize(sourceDir)

    override def mapClasspathEntry(classpathEntry: File): File = classpathEntry //TODO fix this?

    override def mapJavacOption(javacOption: String): String = javacOption
    override def mapScalacOption(scalacOption: String): String = scalacOption

    override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = updateModificationDate(mapBinaryFile(file))
    override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = readSourceStamp(file, sourceStamp)
    override def mapProductStamp(file: File, productStamp: Stamp): Stamp = updateModificationDate(mapProductFile(file))

    override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
  }

  private def updateModificationDate(f: File): Stamp =
    sbt.internal.inc.Stamper.forLastModified(f)


  private lazy val cpMapper = new SbtClasspathMapper(projectRoot, classpath, includeSize)

  private def exportBinaryFile(f: File): File = {
    cpMapper.write(f).map(s => new File(s)).getOrElse(
        if (f.toPath.startsWith(projectRoot)) projectRoot.relativize(f)
        else f
      )
  }

  private def importBinaryFile(exportedFile: File): File = cpMapper.read(exportedFile.toString).getOrElse(
    if (exportedFile.toPath.startsWith(headerPath)) projectRoot.derelativize(exportedFile)
    else exportedFile
  )

  private def readSourceStamp(file: File, loadedStamp: Stamp): Stamp = {
    if (file.exists() && LineAgnosticStamp(file) == loadedStamp) Stamper.forHash(file)
    else loadedStamp
  }

  private def writeSourceStamp(file: File, stamp: Stamp) = if (file.exists()) LineAgnosticStamp(file) else stamp
}
