import model.MsgId
import model.MsgStr
import org.fedorahosted.tennera.jgettext.Message
import org.fedorahosted.tennera.jgettext.PoParser
import java.io.File

fun main(args: Array<String>) {
    if (args.size <= 0) {
        throw IllegalArgumentException("Expected 1 argument.")
    }

    val messageListResult: Result<List<Message>> =
        getFileList(args[0]).flatMap { fileList ->
            fileList.map { file ->
                getMessageListByFile(file)
            }.toResultAndFlatten()
        }

    messageListResult.fold(
        onFailure = { err ->
            err.printStackTrace()
        },
        onSuccess = { messageList ->
            messageList.toMessageMap()
                .filterValues { msgStrSet ->
                    msgStrSet.size >= 2
                }.forEach { (msgId, msgStrSet) ->
                    println("msgId: ${msgId.value}")
                    println("msgStrSet: ${msgStrSet.map { it.value }}")
                    println("=====\n")
                }
        },
    )
}

private fun getFileList(directoryPath: String): Result<List<File>> =
    runCatching {
        File(directoryPath)
            .listFiles()
            ?.toList()
            ?.filter {
                it.isFile
            } ?: emptyList()
    }

private fun getMessageListByFile(file: File): Result<List<Message>> =
    runCatching {
        PoParser()
            .parseCatalog(file)
            .iterator()
            .asSequence()
            .toList()
    }

private fun List<Result<List<Message>>>.toResultAndFlatten(): Result<List<Message>> {
    this.forEach { result ->
        result.fold(
            onFailure = { err -> return Result.failure(err) },
            onSuccess = {},
        )
    }

    return Result.success(
        this.map { it.getOrThrow() }
            .flatten(),
    )
}

private fun <T, R> Result<T>.flatMap(block: (T) -> (Result<R>)): Result<R> =
    this.mapCatching {
        block(it).getOrThrow()
    }

private fun List<Message>.toMessageMap(): Map<MsgId, Set<MsgStr>> =
    this.groupBy { message ->
        message.msgid
    }.filterKeys { nullableKey: String? ->
        nullableKey != null
    }.let { messageMap: Map<String, List<Message>> ->
        messageMap.map { (key, value) ->
            MsgId(key) to value.toMsgStrSet()
        }
    }.toMap()

private fun List<Message>.toMsgStrSet(): Set<MsgStr> =
    this.mapNotNull {
        it.msgstr
    }.map { msgStr ->
        MsgStr(msgStr)
    }.toSet()
