// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sentmessagemap.h"
#include "distributormessagesender.h"
#include "operationstarter.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::framework { struct Clock; }

namespace storage::distributor {

class Operation;

/**
   Storage link that keeps track of running operations.
 */
class OperationOwner : public OperationStarter {
public:

    class Sender : public DistributorMessageSender {
    public:
        Sender(OperationOwner& owner,
               DistributorMessageSender& sender,
               const std::shared_ptr<Operation>& cb)
            : _owner(owner),
              _sender(sender),
              _cb(cb) 
         {}

        void sendCommand(const std::shared_ptr<api::StorageCommand> &) override;
        void sendReply(const std::shared_ptr<api::StorageReply> & msg) override;

        OperationOwner& getOwner() {
            return _owner;
        }

        int getDistributorIndex() const override {
            return _sender.getDistributorIndex();
        }
        
        const vespalib::string& getClusterName() const override {
            return _sender.getClusterName();
        }

        const PendingMessageTracker& getPendingMessageTracker() const override {
            return _sender.getPendingMessageTracker();
        }

    private:
        OperationOwner& _owner;
        DistributorMessageSender& _sender;
        std::shared_ptr<Operation> _cb;
    };

    OperationOwner(DistributorMessageSender& sender,
                   const framework::Clock& clock)
    : _sender(sender),
      _clock(clock) {
    }
    ~OperationOwner() override;

    /**
       Handles replies from storage, mapping from a message id to an operation.

       If the operation was found, returns it in result.first. If the operation was
       done after the reply was processed (no more pending commands), returns true

     */
    bool handleReply(const std::shared_ptr<api::StorageReply>& reply);

    SentMessageMap& getSentMessageMap() {
        return _sentMessageMap;
    };

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override;

    /**
       If the given message exists, create a reply and pass it to the
       appropriate callback.
     */
    void erase(api::StorageMessage::Id msgId);

    [[nodiscard]] DistributorMessageSender& sender() noexcept { return _sender; }

    void onClose();
    uint32_t size() const { return _sentMessageMap.size(); }
    std::string toString() const;

private:
    SentMessageMap _sentMessageMap;
    DistributorMessageSender& _sender;
    const framework::Clock& _clock;
};

}
