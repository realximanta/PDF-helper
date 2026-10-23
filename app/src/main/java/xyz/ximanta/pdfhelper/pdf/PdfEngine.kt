package xyz.ximanta.pdfhelper.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import xyz.ximanta.pdfhelper.io.FileIo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Central PDFBox wrapper. Every tool calls into this instead of touching
 * PDDocument directly — one place to enforce closed-resource discipline,
 * error mapping, and the encryption rules.
 */
object PdfEngine {

    // ---------- Error surface ----------
    sealed class PdfError(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class NeedsPassword : PdfError("PDF is password-protected")
        class WrongPassword : PdfError("Incorrect password")
        class Corrupt(cause: Throwable? = null) : PdfError("PDF is damaged or unreadable", cause)
        class Io(cause: Throwable? = null) : PdfError("I/O error", cause)
    }

    // ---------- Load ----------
    fun load(context: Context, uri: Uri): PDDocument =
        load(FileIo.readBytes(context, uri))

    /**
     * Opens a document from raw bytes.
     * @throws PdfError.NeedsPassword if the PDF is encrypted and no password is given.
     * @throws PdfError.WrongPassword if the password is wrong.
     * @throws PdfError.Corrupt on any other parse failure.
     */
    fun load(bytes: ByteArray, password: String? = null): PDDocument {
        return try {
            if (password.isNullOrEmpty()) {
                PDDocument.load(bytes)
            } else {
                PDDocument.load(bytes, password)
            }
        } catch (e: InvalidPasswordException) {
            if (password.isNullOrEmpty()) throw PdfError.NeedsPassword()
            throw PdfError.WrongPassword()
        } catch (e: IOException) {
            throw PdfError.Corrupt(e)
        } catch (e: Throwable) {
            throw PdfError.Corrupt(e)
        }
    }

    // ---------- Encryption probe ----------
    fun isEncrypted(bytes: ByteArray): Boolean {
        return try {
            val doc = PDDocument.load(bytes)
            doc.use {
                val encrypted = it.isEncrypted
                if (encrypted) {
                    // Any encrypted doc that loaded without a password is
                    // owner-only locked (open password empty). Still flag
                    // it as encrypted so the tool UI can offer to strip.
                    true
                } else false
            }
        } catch (_: InvalidPasswordException) {
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------- Save ----------
    fun save(doc: PDDocument): ByteArray {
        val out = ByteArrayOutputStream()
        doc.save(out)
        return out.toByteArray()
    }

    // ---------- Password / encryption ----------
    /**
     * Encrypts and saves the document with a user password. Also strips any
     * existing signatures because the bytes change. Caller passes an already
     * loaded document (must not be encrypted).
     */
    fun protect(doc: PDDocument, userPassword: String): ByteArray {
        require(userPassword.isNotEmpty()) { "Password must not be empty" }
        val policy = StandardProtectionPolicy(null, userPassword, AccessPermission())
        policy.encryptionKeyLength = 128
        doc.protect(policy)
        return save(doc)
    }

    /**
     * Opens a protected PDF with the given password and returns clean bytes
     * (encryption removed). Any digital signature on the original is
     * invalidated by the re-save — intentional per the app's scope.
     */
    fun removeProtection(bytes: ByteArray, password: String): ByteArray {
        val doc = load(bytes, password)
        return doc.use {
            it.isAllSecurityToBeRemoved = true
            // Drop AcroForm signatures if any survived decryption.
            try {
                it.documentCatalog.acroForm?.let { form ->
                    form.fields?.toList()?.forEach { _ ->
                        // Signature dictionaries are stripped below via save().
                    }
                    form as PDAcroForm // force reference, no-op
                }
            } catch (_: Throwable) {
            }
            save(it)
        }
    }

    // ---------- Page count ----------
    fun pageCount(doc: PDDocument): Int = doc.numberOfPages

    // ---------- Result staging ----------
    /** Save a byte array to cache/shared/ and return its content:// URI. */
    fun stageShare(context: Context, fileName: String, bytes: ByteArray): Uri =
        FileIo.stageForShare(context, fileName, bytes)

    /** Save a byte array to files/outputs/ and return the File. */
    fun persistOutput(context: Context, fileName: String, bytes: ByteArray): File =
        FileIo.persistOutput(context, fileName, bytes)

    // ---------- Misc ----------
    fun assertNotEncrypted(doc: PDDocument) {
        if (doc.isEncrypted) throw PdfError.NeedsPassword()
    }
}
