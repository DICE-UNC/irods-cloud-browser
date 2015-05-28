package org.irods.jargon.idrop.web.services

import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.exception.JargonException
import org.irods.jargon.core.pub.CollectionAO
import org.irods.jargon.core.pub.CollectionAndDataObjectListAndSearchAO
import org.irods.jargon.core.pub.DataObjectAO
import org.irods.jargon.core.pub.IRODSAccessObjectFactory
import org.irods.jargon.core.pub.domain.ObjStat
import org.irods.jargon.core.pub.io.IRODSFile
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry



/**
 * Service for iRODS files (collections and data objects) dealing with file details and operations
 * @author Mike Conway - DICE
 *
 */
class FileService {

	static transactional = false
	IRODSAccessObjectFactory irodsAccessObjectFactory
	JargonServiceFactoryService jargonServiceFactoryService

	/**
	 * Get an objStat for a given path
	 * @param path
	 * @param irodsAccount
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 */
	ObjStat retrieveObjStatForFile(String path, IRODSAccount irodsAccount) throws FileNotFoundException, JargonException {
		log.info("retrieveObjStatForFile()")

		if (!path) {
			throw new IllegalArgumentException("null or empty path")
		}

		if (!irodsAccount) {
			throw new IllegalArgumentException("null or empty irodsAccount")
		}

		log.info("path:${path}")

		CollectionAndDataObjectListAndSearchAO listAndSearch = irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount)
		ObjStat objStat = listAndSearch.retrieveObjectStatForPath(path)
		log.info("objStat:${objStat}")
		return objStat
	}

	/**
	 * Given the path, return the appropriate object from the iRODS catalog
	 * @param path
	 * @param irodsAccount
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 */
	Object retrieveCatalogInfoForPath(String path, IRODSAccount irodsAccount) throws FileNotFoundException, JargonException {

		log.info("retrieveCatalogInfoForPath()")

		if (!path) {
			throw new IllegalArgumentException("null or empty path")
		}

		if (!irodsAccount) {
			throw new IllegalArgumentException("null or empty irodsAccount")
		}

		log.info("path:${path}")

		ObjStat objStat = retrieveObjStatForFile(path, irodsAccount)

		if (objStat.isSomeTypeOfCollection()) {
			log.info("treat as collection")
			CollectionAO collectionAO = irodsAccessObjectFactory.getCollectionAO(irodsAccount)
			return collectionAO.findByAbsolutePath(path)
		} else {
			log.info("treat as data object")
			DataObjectAO dataObjectAO = irodsAccessObjectFactory.getDataObjectAO(irodsAccount)
			return dataObjectAO.findByAbsolutePath(path)
		}
	}

	/**
	 * Receive a handle to an input stream for a single file download
	 * @param path 
	 * @param irodsAccount
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 */
	DownloadFileSpecification obtainInputStreamForDownloadSingleFile(String path, IRODSAccount irodsAccount) throws FileNotFoundException, JargonException {
		log.info("obtainInputStreamForDownloadSingleFile")
		if (!path) {
			throw new IllegalArgumentException("null or missing path")
		}
		if (!irodsAccount) {
			throw new IllegalArgumentException("null irodsAccount")
		}
		log.info("path:${path}")
		log.info("irodsAccount:${irodsAccount}")

		def irodsFileInputStream = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount).instanceIRODSFileInputStream(path)
		IRODSFile irodsFile = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount).instanceIRODSFile(path)
		if (!irodsFile.exists()) {
			throw new FileNotFoundException("file does not exist")
		}
		if (!irodsFile.canRead()) {
			throw new FileNotFoundException("no access to the file")
		}
		def length =  irodsFile.length()
		def name = irodsFile.getName()
		log.info("file length = ${length}")
		log.info("opened input stream")

		def dls = new DownloadFileSpecification()
		dls.contentDispositionHeader = "attachment;filename=\"${irodsFile.name}\""
		dls.length = length
		dls.type = "application/octet-stream"
		dls.inputStream =  irodsFileInputStream
		return dls
	}

	/**
	 * Receive a handle to an input stream for a multiple file download, which will be some kind of bundle
	 * @param paths
	 * @param irodsAccount
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 */
	DownloadFileSpecification obtainInputStreamForDownloadMultipleFiles(List<String> paths, IRODSAccount irodsAccount) throws FileNotFoundException, JargonException {
		log.info("obtainInputStreamForDownloadMultipleFiles")
		if (!paths) {
			throw new IllegalArgumentException("null or missing paths")
		}
		if (!irodsAccount) {
			throw new IllegalArgumentException("null irodsAccount")
		}
		log.info("getting zip service and building bundle file")
		def jargonZipService = jargonServiceFactoryService.instanceJargonZipService(irodsAccount)

		def bundleStreamWrapper = jargonZipService.obtainBundleAsInputStreamWithAdditionalMetadataGivenPaths(paths)
		log.info("..retrieved bundle file as input stream")

		def dls = new DownloadFileSpecification()
		dls.contentDispositionHeader = "attachment;filename=\"${bundleStreamWrapper.bundleFileName}\""
		dls.length = bundleStreamWrapper.length
		dls.type = "application/octet-stream"
		dls.inputStream =  bundleStreamWrapper.inputStream
		dls.bundleFileName = bundleStreamWrapper.bundleFileName

		return dls
	}

	/**
	 * Delete irods files/folders, taking a list of files so multi-selects can be supported
	 * @param paths <code>List<String></code> of file paths
	 * @param force <code>boolean</code> whether to force delete
	 * @param irodsAccount
	 * @throws JargonException
	 */
	void delete(List<String> paths, boolean force, IRODSAccount irodsAccount) throws JargonException {
		log.info("delete()")
		if (!paths) {
			throw new IllegalArgumentException("path is missing")
		}
		if (!irodsAccount) {
			throw new IllegalArgumentException("irodsAccount is missing")
		}
		log.info("force:${force}")
		def irodsFileFactory = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount)
		def irodsFile
		paths.each{ pathElem ->
			log.info("deleting:${pathElem}")
			irodsFile = irodsFileFactory.instanceIRODSFile(pathElem)
			log.info("irodsFile for delete:${irodsFile}")
			if (force) {
				irodsFile.deleteWithForceOption()
			} else {
				irodsFile.delete()
			}
		}
		log.info("done")
	}

	/**
	 * Idempotent method to create a directory and return a listing entry.  This is so that the entry can easily be 
	 * inserted into an interface
	 * @param path <code>String</code> with an iRODS path
	 * @param irodsAccount
	 * @return <code>CollectionAndDataObjectListingEntry</code> with the 
	 * @throws JargonException
	 */
	CollectionAndDataObjectListingEntry newFolder(String path, IRODSAccount irodsAccount) throws JargonException {
		log.info("newFolder()")
		if (!path) {
			throw new IllegalArgumentException("null or empty path")
		}
		if (!irodsAccount) {
			throw new IllegalArgumentException("irodsAccount is missing")
		}
		def irodsFile = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount).instanceIRODSFile(path)
		irodsFile.mkdir()

		log.info("dir made, return a listing entry")
		def collectionAO = irodsAccessObjectFactory.getCollectionAO(irodsAccount)
		def listingEntry = collectionAO.getListingEntryForAbsolutePath(path)
		return listingEntry
	}

	/**
	 * Rename a file to a new name within the same parent collection
	 * @param path <code>String</code> with an iRODS path that is the existing path of the file or collection
	 * @param newName <code>String</code> with the new name, in the existing parent collection (this is different from a move)
	 * @param irodsAccount
	 * @return
	 * @throws JargonException
	 */
	CollectionAndDataObjectListingEntry rename(String path, String newName, IRODSAccount irodsAccount) throws JargonException {
		log.info("rename()")
		if (!path) {
			throw new IllegalArgumentException("null or empty path")
		}
		if (!newName) {
			throw new IllegalArgumentException("null or empty newName")
		}
		if (!irodsAccount) {
			throw new IllegalArgumentException("irodsAccount is missing")
		}

		log.info("path:${path}")
		log.info("newName:${newName}")

		def dataTransferOperations = irodsAccessObjectFactory.getDataTransferOperations(irodsAccount)
		def irodsFileFactory = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount)
		def sourceFile = irodsFileFactory.instanceIRODSFile(path)
		def targetFile = irodsFileFactory.instanceIRODSFile(sourceFile.parent, newName)
		log.info("target is:${targetFile}...doing move...")
		dataTransferOperations.move(sourceFile, targetFile)
		log.info("move completed")
		def collectionAO = irodsAccessObjectFactory.getCollectionAO(irodsAccount)
		def listingEntry = collectionAO.getListingEntryForAbsolutePath(targetFile.absolutePath)
		log.info("entry for new file:${listingEntry}")
		return listingEntry
	}
}
